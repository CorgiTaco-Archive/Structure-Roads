package corgitaco.modid.river;

import com.mojang.serialization.Codec;
import corgitaco.modid.Main;
import corgitaco.modid.mixin.access.ChunkManagerAccess;
import corgitaco.modid.mixin.access.StructureAccess;
import corgitaco.modid.util.BiomeUtils;
import corgitaco.modid.util.fastnoise.FastNoise;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.feature.structure.VillageConfig;
import net.minecraft.world.gen.placement.NoPlacementConfig;
import net.minecraft.world.gen.placement.Placement;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class WorldStructureAwarePathGenerator extends Feature<NoFeatureConfig> {

    public static final boolean DEBUG_ANGLES = false;

    public static final Feature<NoFeatureConfig> PATH = BiomeUtils.createFeature("structure_aware_path", new WorldStructureAwarePathGenerator(NoFeatureConfig.CODEC));

    public static final ConfiguredFeature<?, ?> CONFIGURED_PATH = BiomeUtils.createConfiguredFeature("structure_aware_path", PATH.configured(NoFeatureConfig.INSTANCE).decorated(Placement.NOPE.configured(NoPlacementConfig.INSTANCE)));

    public WorldStructureAwarePathGenerator(Codec<NoFeatureConfig> codec) {
        super(codec);
    }

    private final Map<World, Long2ReferenceOpenHashMap<LongSet>> structurePositions = new Object2ObjectArrayMap<>();
    private final Map<World, Long2ReferenceOpenHashMap<ArrayList<StartEndPathGenerator>>> regionPathGenerators = new Object2ObjectArrayMap<>();
    private final Map<ServerWorld, Path> worldStructuresCache = new Object2ObjectArrayMap<>();
    private final Map<ServerWorld, Path> worldGeneratorsCache = new Object2ObjectArrayMap<>();

    public static long regionLong(int regionX, int regionZ) {
        return (long) regionX & 4294967295L | ((long) regionZ & 4294967295L) << 32;
    }

    public static int chunkToRegion(int coord) {
        return coord >> 8;
    }

    public static int blockToRegion(int coord) {
        return coord >> 12;
    }

    public static int regionToBlock(int coord) {
        return coord << 12;
    }

    public static int regionToChunk(int coord) {
        return coord << 8;
    }

    public static int regionToMaxChunk(int coord) {
        return regionToChunk(coord + 1) - 1;
    }

    @Override
    public boolean place(ISeedReader worldRegion, ChunkGenerator generator, Random rand, BlockPos pos, NoFeatureConfig config) {
        ServerWorld serverLevel = worldRegion.getLevel();

        long seed = worldRegion.getSeed();

        int searchRangeInChunks = 1000;
        int searchRangeInRegions = chunkToRegion(searchRangeInChunks);

        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        long currentChunk = ChunkPos.asLong(chunkX, chunkZ);

        int currentRegionX = chunkToRegion(chunkX);
        int currentRegionZ = chunkToRegion(chunkZ);
        long currentRegion = regionLong(chunkToRegion(chunkX), chunkToRegion(chunkZ));

        Path structureStorageDir = this.worldStructuresCache.computeIfAbsent(serverLevel, (level) -> {
            Path storageFolder = ((ChunkManagerAccess) level.getChunkSource().chunkMap).getStorageFolder().toPath().resolve(Main.MOD_ID).resolve("structures");
            if (!storageFolder.toFile().exists()) {
                try {
                    Files.createDirectories(storageFolder);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return storageFolder;
        });

        Path generatorStorageDir = this.worldGeneratorsCache.computeIfAbsent(serverLevel, (level) -> {
            Path storageFolder = ((ChunkManagerAccess) level.getChunkSource().chunkMap).getStorageFolder().toPath().resolve(Main.MOD_ID).resolve("generators");
            if (!storageFolder.toFile().exists()) {
                try {
                    Files.createDirectories(storageFolder);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return storageFolder;
        });

        BiomeProvider biomeSource = generator.getBiomeSource();

        Structure<VillageConfig> village = Structure.VILLAGE;

        StructureSeparationSettings structureSeperationSettings = generator.getSettings().structureConfig().get(village);
        int spacing = structureSeperationSettings.spacing();

        Long2ReferenceOpenHashMap<LongSet> regionPositions = this.structurePositions.computeIfAbsent(serverLevel, (level1) -> new Long2ReferenceOpenHashMap<>());
        Long2ReferenceOpenHashMap<ArrayList<StartEndPathGenerator>> regionPathGenerators = this.regionPathGenerators.computeIfAbsent(serverLevel, (level1) -> new Long2ReferenceOpenHashMap<>());

        for (int regionX = currentRegionX - searchRangeInRegions; regionX < currentRegionX + searchRangeInRegions; regionX++) {
            for (int regionZ = currentRegionZ - searchRangeInRegions; regionZ < currentRegionZ + searchRangeInRegions; regionZ++) {
                long activeRegion = regionLong(regionX, regionZ);

                if (!regionPositions.containsKey(activeRegion)) {
                    addRegionStructuresToCache(seed, structureStorageDir, biomeSource, village, structureSeperationSettings, spacing, regionPositions, regionX, regionZ, activeRegion);
                }
            }
        }

        if (regionPositions.containsKey(currentRegion) && !regionPathGenerators.containsKey(currentRegion)) {
            addToGeneratorsToCache(worldRegion, seed, currentRegionX, currentRegionZ, currentRegion, generatorStorageDir, structureSeperationSettings, regionPositions, regionPathGenerators);
        }


        if (regionPathGenerators.containsKey(currentRegion)) {
            ArrayList<StartEndPathGenerator> startEndPathGenerators = regionPathGenerators.get(currentRegion);

            for (StartEndPathGenerator startEndPathGenerator : startEndPathGenerators) {
                if (startEndPathGenerator.getNodeChunkPositions().contains(currentChunk)) {
                    for (StartEndPathGenerator.Node node : startEndPathGenerator.getNodesForChunk(currentChunk)) {
                        generateForNode(worldRegion, chunkX, chunkZ, node, startEndPathGenerator);
                    }
                }
            }
        }

        return true;
    }


    // Structure position Caching

    /************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************/
    private void addRegionStructuresToCache(long seed, Path structureStorageDir, BiomeProvider biomeSource, Structure<?> village, StructureSeparationSettings structureSeperationSettings, int spacing, Long2ReferenceOpenHashMap<LongSet> regionPositions, int regionX, int regionZ, long activeRegion) {
        String fileName = regionX + "," + regionZ + ".2dr";
        File file = structureStorageDir.resolve(fileName).toFile();
        if (!file.exists()) {
            scanRegion(seed, biomeSource, village, structureSeperationSettings, spacing, regionPositions, regionX, regionZ);

            saveNewStructureRegion(regionPositions, village, activeRegion, file);
        } else {
            readStructuresForRegion(regionPositions, village, activeRegion, file);
        }
    }

    private void scanRegion(long seed, BiomeProvider biomeSource, Structure<?> village, StructureSeparationSettings structureSeperationSettings, int spacing, Long2ReferenceOpenHashMap<LongSet> regionPositions, int regionX, int regionZ) {
        int activeMinChunkX = regionToChunk(regionX);
        int activeMinChunkZ = regionToChunk(regionZ);

        int activeMaxChunkX = regionToMaxChunk(regionX);
        int activeMaxChunkZ = regionToMaxChunk(regionZ);

        int activeMinGridX = Math.floorDiv(activeMinChunkX, spacing);
        int activeMinGridZ = Math.floorDiv(activeMinChunkZ, spacing);
        int activeMaxGridX = Math.floorDiv(activeMaxChunkX, spacing);
        int activeMaxGridZ = Math.floorDiv(activeMaxChunkZ, spacing);

        scanRegionStructureGrid(seed, biomeSource, village, structureSeperationSettings, spacing, regionPositions, activeMinGridX, activeMinGridZ, activeMaxGridX, activeMaxGridZ);
    }

    private void readStructuresForRegion(Long2ReferenceOpenHashMap<LongSet> regionPositions, Structure<?> structure, long activeRegion, File file) {
        String structureName = structure.getFeatureName() + "_positions";
        try {
            CompoundNBT readTag = CompressedStreamTools.read(file);
            if (readTag.contains(structureName)) {
                long[] structurePositions = readTag.getLongArray(structureName);
                regionPositions.put(activeRegion, new LongArraySet(structurePositions));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveNewStructureRegion(Long2ReferenceOpenHashMap<LongSet> regionPositions, Structure<?> structure, long activeRegion, File file) {
        String structureName = structure.getFeatureName() + "_positions";
        CompoundNBT nbt = new CompoundNBT();
        nbt.putLongArray(structureName, regionPositions.get(activeRegion).toLongArray());
        try {
            CompressedStreamTools.write(nbt, file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates the cache of all structure positions for the given region
     */
    private void scanRegionStructureGrid(long seed, BiomeProvider biomeSource, Structure<?> village, StructureSeparationSettings structureSeparationSettings, int spacing, Long2ReferenceOpenHashMap<LongSet> regionPositions, int activeMinGridX, int activeMinGridZ, int activeMaxGridX, int activeMaxGridZ) {
        for (int structureGridX = activeMinGridX; structureGridX <= activeMaxGridX; structureGridX++) {
            for (int structureGridZ = activeMinGridZ; structureGridZ <= activeMaxGridZ; structureGridZ++) {
                long structureChunkPos = getStructureChunkPos(village, seed, structureSeparationSettings.salt(), new SharedSeedRandom(), spacing, structureSeparationSettings.separation(), structureGridX, structureGridZ);

                int structureChunkPosX = ChunkPos.getX(structureChunkPos);
                int structureChunkPosZ = ChunkPos.getZ(structureChunkPos);

                long structureRegionLong = regionLong(chunkToRegion(structureChunkPosX), chunkToRegion(structureChunkPosZ));

                ChunkPos chunkPos = village.getPotentialFeatureChunk(structureSeparationSettings, seed, new SharedSeedRandom(), structureChunkPosX, structureChunkPosZ);

                if (chunkPos.x == structureChunkPosX && chunkPos.z == structureChunkPosZ && sampleAndTestChunkBiomesForStructure(structureChunkPosX, structureChunkPosZ, biomeSource, village)) {
                    regionPositions.computeIfAbsent(structureRegionLong, (value) -> new LongArraySet()).add(structureChunkPos);
                }
            }
        }
    }
    /************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************/


    // Path Generators

    /************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************/
    private void addToGeneratorsToCache(ISeedReader worldRegion, long seed, int currentRegionX, int currentRegionZ, long currentRegion, Path generatorStorageDir, StructureSeparationSettings structureSeperationSettings, Long2ReferenceOpenHashMap<LongSet> regionPositions, Long2ReferenceOpenHashMap<ArrayList<StartEndPathGenerator>> regionPathGenerators) {
        String fileName = currentRegionX + "," + currentRegionZ + ".2dr";
        File file = generatorStorageDir.resolve(fileName).toFile();

        if (!file.exists()) {
            List<StartEndPathGenerator> startEndPathGenerators = processPathGeneratorsForRegion(worldRegion, seed, currentRegion, structureSeperationSettings, regionPositions, regionPathGenerators);

            saveGeneratorsToDisk(file, startEndPathGenerators);
        } else {
            readGeneratorsFromDisk(currentRegion, regionPathGenerators, file);
        }
    }

    private void readGeneratorsFromDisk(long currentRegion, Long2ReferenceOpenHashMap<ArrayList<StartEndPathGenerator>> regionPathGenerators, File file) {
        ArrayList<StartEndPathGenerator> startEndPathGenerators = regionPathGenerators.computeIfAbsent(currentRegion, (regionLong1) -> new ArrayList<>());
        try {
            CompoundNBT readTag = CompressedStreamTools.read(file);
            ListNBT generators = readTag.getList("generators", 10);

            for (INBT inbt : generators) {
                startEndPathGenerators.add(StartEndPathGenerator.read((CompoundNBT) inbt));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveGeneratorsToDisk(File file, List<StartEndPathGenerator> startEndPathGenerators) {
        CompoundNBT nbt = new CompoundNBT();
        ListNBT generators = new ListNBT();
        for (StartEndPathGenerator startEndPathGenerator : startEndPathGenerators) {
            generators.add(startEndPathGenerator.write());
        }

        nbt.put("generators", generators);

        try {
            CompressedStreamTools.write(nbt, file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<StartEndPathGenerator> processPathGeneratorsForRegion(ISeedReader worldRegion, long seed, long currentRegion, StructureSeparationSettings structureSeparationSettings, Long2ReferenceOpenHashMap<LongSet> regionPositions, Long2ReferenceOpenHashMap<ArrayList<StartEndPathGenerator>> regionPathGenerators) {
        ArrayList<StartEndPathGenerator> startEndPathGenerators = regionPathGenerators.computeIfAbsent(currentRegion, (regionLong1) -> new ArrayList<>());
        LongSet regionStructurePositions = regionPositions.get(currentRegion);
        LongSet createdPaths = new LongArraySet();

        if (!regionStructurePositions.isEmpty()) {
            long[] structurePositions = regionStructurePositions.toLongArray();

            for (int idx = 0; idx < structurePositions.length; idx++) {
                long startStructurePos = structurePositions[idx];
                for (int idx1 = 0; idx1 < structurePositions.length; idx1++) {
                    if (idx == idx1) {
                        continue;
                    }

                    long endStructurePos = structurePositions[idx1];

                    // If we've already created a path between these points, let's NOT do it again.
                    if (createdPaths.contains(startStructurePos + endStructurePos)) {
                        continue;
                    }

                    tryAddPathGeneratorForRegion(worldRegion, seed, structureSeparationSettings, startEndPathGenerators, createdPaths, startStructurePos, endStructurePos);
                }
            }
        }
        return startEndPathGenerators;
    }

    private void tryAddPathGeneratorForRegion(ISeedReader worldRegion, long seed, StructureSeparationSettings structureSeparationSettings, ArrayList<StartEndPathGenerator> startEndPathGenerators, LongSet createdPaths, long startStructurePos, long endStructurePos) {
        int startX = ChunkPos.getX(startStructurePos);
        int startZ = ChunkPos.getZ(startStructurePos);
        BlockPos startPos = new BlockPos(SectionPos.sectionToBlockCoord(startX), 0, SectionPos.sectionToBlockCoord(startZ));

        int endX = ChunkPos.getX(endStructurePos);
        int endZ = ChunkPos.getZ(endStructurePos);
        BlockPos endPos = new BlockPos(SectionPos.sectionToBlockCoord(endX), 0, SectionPos.sectionToBlockCoord(endZ));


        SharedSeedRandom random = new SharedSeedRandom();

        random.setLargeFeatureWithSalt(seed, Math.floorDiv(startX + 1, endX + 1), Math.floorDiv(startZ + 1, endZ + 1), structureSeparationSettings.salt());

        StartEndPathGenerator startEndPathGenerator = getPathGenerator(worldRegion, random, startStructurePos, endStructurePos, startPos, endPos, startEndPathGenerators);
        if (startEndPathGenerator != null) {
            createdPaths.add(startStructurePos + endStructurePos);
            startEndPathGenerators.add(startEndPathGenerator);
            Main.LOGGER.info(String.format("/tp %s ~ %s - /tp %s ~ %s", startPos.getX(), startPos.getZ(), endPos.getX(), endPos.getZ()));
        }
    }

    /**
     * Add a path generator to this region's cache and cache how many times a given position has succeeded.
     */
    @Nullable
    private StartEndPathGenerator getPathGenerator(ISeedReader worldRegion, Random random, long startStructurePos, long endStructurePos, BlockPos startPos, BlockPos endPos, List<StartEndPathGenerator> generators) {
        float degreesRotated = 0.0F;

        FastNoise noise = createNoise(random.nextInt());
        StartEndPathGenerator startEndPathGenerator = new StartEndPathGenerator(noise, startPos, endPos, (node -> isNodeInvalid(node, worldRegion, pathBox(startStructurePos, endStructurePos, random), startStructurePos)), node -> {
            BlockPos nodePos = node.getPos();
            int nodeChunkX = SectionPos.blockToSectionCoord(nodePos.getX());
            int nodeChunkZ = SectionPos.blockToSectionCoord(nodePos.getZ());
            long nodeChunk = ChunkPos.asLong(nodeChunkX, nodeChunkZ);

            return nodeChunk == endStructurePos;

        }, 5000, degreesRotated, 5);

        while (!startEndPathGenerator.exists() && degreesRotated <= (Math.PI * 2) - StartEndPathGenerator.DEGREE_ROTATION) {
            degreesRotated += StartEndPathGenerator.DEGREE_ROTATION;
            startEndPathGenerator = new StartEndPathGenerator(createNoise(random.nextInt()), startPos, endPos, (node -> isNodeInvalid(node, worldRegion, pathBox(startStructurePos, endStructurePos, random), startStructurePos)), node -> {
                BlockPos nodePos = node.getPos();
                int nodeChunkX = SectionPos.blockToSectionCoord(nodePos.getX());
                int nodeChunkZ = SectionPos.blockToSectionCoord(nodePos.getZ());
                long nodeChunk = ChunkPos.asLong(nodeChunkX, nodeChunkZ);

                return nodeChunk == endStructurePos || intersectsGeneratorPos(nodeChunk, generators);

            }, 5000, degreesRotated, 5);
        }
        return !startEndPathGenerator.exists() ? null : startEndPathGenerator;
    }

    private boolean intersectsGeneratorPos(long nodeChunk, List<StartEndPathGenerator> generators) {
        for (StartEndPathGenerator generator : generators) {
            if (generator.getNodeChunkPositions().contains(nodeChunk)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returning true tells the Path generator to recompute the angle of the pos used to create this position w/ a different angle.
     */
    private boolean isNodeInvalid(StartEndPathGenerator.Node node, ISeedReader world, MutableBoundingBox pathBox, long startPos) {
        BlockPos nodePos = node.getPos();
        int nodeChunkX = SectionPos.blockToSectionCoord(nodePos.getX());
        int nodeChunkZ = SectionPos.blockToSectionCoord(nodePos.getZ());

        Biome noiseBiome = world.getBiome(nodePos);
        Biome.Category biomeCategory = noiseBiome.getBiomeCategory();
        if (biomeCategory == Biome.Category.OCEAN) {
            return true;
        }

        if (!pathBox.intersects(nodeChunkX, nodeChunkZ, nodeChunkX, nodeChunkZ)) {
            return true;
        }

        return false;
    }

    /************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************/


    // World Generation
    /************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************/
    /**
     * If the node from the Path generator intersects the current chunk, generate.
     */
    private void generateForNode(ISeedReader worldRegion, int chunkX, int chunkZ, StartEndPathGenerator.Node node, StartEndPathGenerator pathGenerator) {
        BlockPos.Mutable mutable = new BlockPos.Mutable().set(node.getPos());
        mutable.setY(worldRegion.getHeight(Heightmap.Type.WORLD_SURFACE_WG, mutable.getX(), mutable.getZ()) - 1);

        int size = 2;

        List<StartEndPathGenerator.Node> nodes = pathGenerator.getNodes();

        int prevIdx = node.getIdx() - 1;

        @Nullable
        StartEndPathGenerator.Node prevNode = 0 <= prevIdx ? nodes.get(prevIdx) : null;

        if (prevNode != null) {
            if (prevNode.getGeneratedForNode() <= pathGenerator.getDistanceBetweenNodes()) {
                generateBlocksForNode(worldRegion, chunkX, chunkZ, pathGenerator, size, prevNode);
            }
        }
        generateBlocksForNode(worldRegion, chunkX, chunkZ, pathGenerator, size, node);
    }

    private void generateBlocksForNode(ISeedReader worldRegion, int chunkX, int chunkZ, StartEndPathGenerator pathGenerator, int size, StartEndPathGenerator.Node node) {
        int nodeX = node.getPos().getX();
        int nodeZ = node.getPos().getZ();

        BlockPos.Mutable mutable1 = new BlockPos.Mutable();
        if (DEBUG_ANGLES) {
            debugFailedAngles(worldRegion, node, mutable1);
        }

        for (int i = node.getGeneratedForNode(); i <= pathGenerator.getDistanceBetweenNodes(); i++) {
            Vector3i angleOffset = pathGenerator.getAngleOffset(pathGenerator.getNoise().GetNoise(nodeX, 0, nodeZ), i);
            int subNodeX = nodeX + angleOffset.getX();
            int subNodeZ = nodeZ + angleOffset.getZ();
            int subNodeChunkX = SectionPos.blockToSectionCoord(subNodeX);
            int subNodeChunkZ = SectionPos.blockToSectionCoord(subNodeZ);

            if (chunkX == subNodeChunkX && subNodeChunkZ == chunkZ) {
                for (int xMove = -size; xMove < size; xMove++) {
                    for (int zMove = -size; zMove < size; zMove++) {
                        int blockX = subNodeX + xMove;
                        int blockZ = subNodeZ + zMove;
                        mutable1.set(blockX, worldRegion.getHeight(Heightmap.Type.WORLD_SURFACE_WG, blockX, blockZ) - 1, blockZ);
                        worldRegion.setBlock(mutable1, Blocks.COBBLESTONE.defaultBlockState(), 2);
                    }
                }
                node.setGeneratedForNode(i);
            } else {
                break;
            }
        }
    }

    private void debugAllAngles(ISeedReader worldRegion, StartEndPathGenerator pathGenerator, StartEndPathGenerator.Node node, int nodeX, int nodeZ, BlockPos.Mutable mutable1) {
        if (node.getIdx() % 5 == 0) {

            mutable1.set(nodeX, worldRegion.getHeight(Heightmap.Type.WORLD_SURFACE_WG, nodeX, nodeZ) - 1, nodeZ);
            for (int height = 0; height < 7; height++) {
                worldRegion.setBlock(mutable1.move(Direction.UP), Blocks.EMERALD_BLOCK.defaultBlockState(), 2);
            }

            double degreesRotated = StartEndPathGenerator.DEGREE_ROTATION;
            while (degreesRotated <= Math.PI * 2) {
                Vector3i angleOffset = pathGenerator.getAngleOffset((float) (pathGenerator.getNoise().GetNoise(nodeX, 0, nodeZ) + degreesRotated));

                int subNodeX = nodeX + angleOffset.getX();
                int subNodeZ = nodeZ + angleOffset.getZ();

                mutable1.set(subNodeX, worldRegion.getHeight(Heightmap.Type.WORLD_SURFACE_WG, subNodeX, subNodeZ) - 1, subNodeZ);
                for (int height = 0; height < 7; height++) {
                    worldRegion.setBlock(mutable1.move(Direction.UP), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
                }
                degreesRotated += StartEndPathGenerator.DEGREE_ROTATION;
            }
        }
    }

    private void debugFailedAngles(ISeedReader worldRegion, StartEndPathGenerator.Node node, BlockPos.Mutable mutable1) {
        for (BlockPos failedPosition : node.getFailedPositions()) {
            mutable1.set(failedPosition.getX(), worldRegion.getHeight(Heightmap.Type.WORLD_SURFACE_WG, failedPosition.getX(), failedPosition.getZ()) - 1, failedPosition.getZ());
            for (int height = 0; height < 7; height++) {
                worldRegion.setBlock(mutable1.move(Direction.UP), Blocks.EMERALD_BLOCK.defaultBlockState(), 2);
            }
        }
    }

    /************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************/


    public static FastNoise createNoise(int seed) {
        FastNoise noise = new FastNoise(seed);
        noise.SetNoiseType(FastNoise.NoiseType.PerlinFractal);
        noise.SetGradientPerturbAmp(1);
        noise.SetFractalOctaves(5);
        noise.SetFractalGain(0.5f);
        noise.SetFrequency(0.08F / 5);
        return noise;
    }

    private MutableBoundingBox pathBox(long startPos, long endPos, Random random) {
        int startPosChunkX = ChunkPos.getX(startPos);
        int startPosChunkZ = ChunkPos.getZ(startPos);

        int endPosChunkX = ChunkPos.getX(endPos);
        int endPosChunkZ = ChunkPos.getZ(endPos);

        boolean flipX = startPosChunkX > endPosChunkX;
        boolean flipZ = startPosChunkZ > endPosChunkZ;
        MutableBoundingBox structureBox = new MutableBoundingBox(flipX ? endPosChunkX : startPosChunkX, 0, flipZ ? endPosChunkZ : startPosChunkZ, flipX ? startPosChunkX : endPosChunkZ, 0, flipZ ? startPosChunkZ : endPosChunkZ);

        MutableBoundingBox pathBox;

        if (structureBox.getXSpan() > structureBox.getZSpan()) {
            pathBox = new MutableBoundingBox(structureBox.x0 - random.nextInt(5) - 2, 0, structureBox.z0, structureBox.x1 + random.nextInt(5) + 2, 0, structureBox.z1);
        } else if (structureBox.getZSpan() > structureBox.getXSpan()) {
            pathBox = new MutableBoundingBox(structureBox.x0, 0, structureBox.z0 - random.nextInt(5) - 2, structureBox.x1, 0, structureBox.z1 + random.nextInt(5) + 2);
        } else {
            pathBox = structureBox;
        }
        return pathBox;
    }

    public boolean sampleAndTestChunkBiomesForStructure(int chunkX, int chunkZ, BiomeManager.IBiomeReader biomeReader, Structure<?> structure) {
        return biomeReader.getNoiseBiome((chunkX << 2) + 2, 0, (chunkZ << 2) + 2).getGenerationSettings().isValidStart(structure);
    }

    private long getStructureChunkPos(Structure<?> structure, long worldSeed, int structureSalt, SharedSeedRandom seedRandom, int spacing, int separation, int floorDivX, int floorDivZ) {
        int x;
        int z;
        seedRandom.setLargeFeatureWithSalt(worldSeed, floorDivX, floorDivZ, structureSalt);

        if (((StructureAccess) structure).invokeLinearSeparation()) {
            x = seedRandom.nextInt(spacing - separation);
            z = seedRandom.nextInt(spacing - separation);
        } else {
            x = (seedRandom.nextInt(spacing - separation) + seedRandom.nextInt(spacing - separation)) / 2;
            z = (seedRandom.nextInt(spacing - separation) + seedRandom.nextInt(spacing - separation)) / 2;
        }
        return ChunkPos.asLong(floorDivX * spacing + x, floorDivZ * spacing + z);
    }
}
