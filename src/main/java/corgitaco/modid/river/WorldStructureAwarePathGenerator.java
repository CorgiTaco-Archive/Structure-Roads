package corgitaco.modid.river;

import com.mojang.serialization.Codec;
import corgitaco.modid.mixin.access.StructureAccess;
import corgitaco.modid.util.BiomeUtils;
import corgitaco.modid.util.fastnoise.FastNoise;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.block.Blocks;
import net.minecraft.util.Direction;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.math.SectionPos;
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

import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

public class WorldStructureAwarePathGenerator extends Feature<NoFeatureConfig> {

    public static final Feature<NoFeatureConfig> PATH = BiomeUtils.createFeature("structure_aware_path", new WorldStructureAwarePathGenerator(NoFeatureConfig.CODEC));

    public static final ConfiguredFeature<?, ?> CONFIGURED_PATH = BiomeUtils.createConfiguredFeature("structure_aware_path", PATH.configured(NoFeatureConfig.INSTANCE).decorated(Placement.NOPE.configured(NoPlacementConfig.INSTANCE)));

    public WorldStructureAwarePathGenerator(Codec<NoFeatureConfig> codec) {
        super(codec);
    }

    private static long seed;

    public static FastNoise noise;

    private final Map<World, Long2ReferenceOpenHashMap<LongSet>> missedChunks = new Object2ObjectArrayMap<>();
    private final Map<World, Long2ReferenceOpenHashMap<LongSet>> structurePositions = new Object2ObjectArrayMap<>();
    private final Map<World, MutableBoundingBox> searchedBox = new Object2ObjectArrayMap<>();
    private final Map<World, Long2IntArrayMap> structureChunkPosToConnectedPathCount = new Object2ObjectArrayMap<>();
    private final Map<World, Long2ReferenceOpenHashMap<ArrayList<StartEndPathGenerator>>> regionPathGenerators = new Object2ObjectArrayMap<>();
    private final Map<World, Long2LongArrayMap> successPairPositionsLookup = new Object2ObjectArrayMap<>();

    private boolean cached = false;

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

    @Override
    public boolean place(ISeedReader worldRegion, ChunkGenerator generator, Random rand, BlockPos pos, NoFeatureConfig config) {
        long seed = worldRegion.getSeed();
        setupNoise(seed);

        int searchRangeInChunks = 1000;
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());

        BiomeProvider biomeSource = generator.getBiomeSource();

        Structure<VillageConfig> village = Structure.VILLAGE;

        StructureSeparationSettings structureSeperationSettings = generator.getSettings().structureConfig().get(village);
        int spacing = structureSeperationSettings.spacing();

        int currentGridX = Math.floorDiv(chunkX, spacing);
        int currentGridZ = Math.floorDiv(chunkZ, spacing);

        int actualMinGridX = Math.floorDiv(chunkX - searchRangeInChunks, spacing);
        int actualMinGridZ = Math.floorDiv(chunkZ - searchRangeInChunks, spacing);
        int actualMaxGridX = Math.floorDiv(chunkX + searchRangeInChunks, spacing);
        int actualMaxGridZ = Math.floorDiv(chunkZ + searchRangeInChunks, spacing);

        ServerWorld serverLevel = worldRegion.getLevel();

        Long2ReferenceOpenHashMap<LongSet> missedChunks = this.missedChunks.computeIfAbsent(serverLevel, (level) -> new Long2ReferenceOpenHashMap<>());
        Long2ReferenceOpenHashMap<LongSet> regionPositions = this.structurePositions.computeIfAbsent(serverLevel, (level1) -> new Long2ReferenceOpenHashMap<>());
        Long2ReferenceOpenHashMap<ArrayList<StartEndPathGenerator>> regionPathGenerators = this.regionPathGenerators.computeIfAbsent(serverLevel, (level1) -> new Long2ReferenceOpenHashMap<>());
        Long2IntArrayMap structureChunkPosToConnectedPathCount = this.structureChunkPosToConnectedPathCount.computeIfAbsent(serverLevel, (level1) -> new Long2IntArrayMap());
        Long2LongArrayMap successPairPositionsLookup = this.successPairPositionsLookup.computeIfAbsent(serverLevel, (level) -> new Long2LongArrayMap());
        MutableBoundingBox searchedRange = this.searchedBox.get(serverLevel);

        if (!cached) {
            createCache(seed, serverLevel, missedChunks, regionPositions, biomeSource, village, structureSeperationSettings, spacing, actualMinGridX, actualMinGridZ, actualMaxGridX, actualMaxGridZ, searchedRange);
        }
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);

        long regionLong = regionLong(chunkToRegion(chunkX), chunkToRegion(chunkZ));

        if (regionPositions.containsKey(regionLong)) {
            LongSet regionStructurePositions = regionPositions.get(regionLong);
            if (!regionStructurePositions.isEmpty()) {
                addPathGenerators(worldRegion, generator, rand, seed, regionPositions, successPairPositionsLookup, regionPathGenerators, structureChunkPosToConnectedPathCount, structureSeperationSettings, regionLong, regionStructurePositions);
            }
        }

        if (regionPathGenerators.containsKey(regionLong)) {
            ArrayList<StartEndPathGenerator> startEndPathGenerators = regionPathGenerators.get(regionLong);

            for (StartEndPathGenerator startEndPathGenerator : startEndPathGenerators) {
                if (startEndPathGenerator.getNodeChunkPositions().contains(chunkKey)) {
                    for (StartEndPathGenerator.Node node : startEndPathGenerator.getNodesForChunk(chunkKey)) {
                        generateForNode(worldRegion, node);
                    }
                }
            }
        }

        return true;
    }

    /**
     * Creates the cache of all structure positions.
     */
    private void createCache(long seed, ServerWorld serverLevel, Long2ReferenceOpenHashMap<LongSet> missedChunks, Long2ReferenceOpenHashMap<LongSet> regionPositions, BiomeProvider biomeSource, Structure<VillageConfig> village, StructureSeparationSettings structureSeperationSettings, int spacing, int actualMinGridX, int actualMinGridZ, int actualMaxGridX, int actualMaxGridZ, MutableBoundingBox searchedRange) {
        cached = true;
        int minGridX;
        int minGridZ;
        int maxGridX;
        int maxGridZ;

        if (searchedRange != null) {
            minGridX = Math.max(actualMinGridX, searchedRange.x0);
            minGridZ = Math.max(actualMinGridZ, searchedRange.z0);
            maxGridX = actualMaxGridX;
            maxGridZ = actualMaxGridZ;
        } else {
            minGridX = actualMinGridX;
            minGridZ = actualMinGridZ;
            maxGridX = actualMaxGridX;
            maxGridZ = actualMaxGridZ;
        }


        for (int structureGridX = minGridX; structureGridX <= maxGridX; structureGridX++) {
            for (int structureGridZ = minGridZ; structureGridZ <= maxGridZ; structureGridZ++) {
                ChunkPos structureChunkPos = getStructureChunkPos(village, seed, structureSeperationSettings.salt(), new SharedSeedRandom(), spacing, structureSeperationSettings.separation(), structureGridX, structureGridZ);
                long structureRegionLong = regionLong(chunkToRegion(structureChunkPos.x), chunkToRegion(structureChunkPos.z));

                long currentChunk = ChunkPos.asLong(structureChunkPos.x, structureChunkPos.z);

//                if (searchedRange != null && searchedRange.intersects(structureGridX, structureGridZ, structureGridX, structureGridZ)) {
//                    structureGridX = minGridX + searchedRange.getXSpan() - 1;
//                    structureGridZ = minGridZ + searchedRange.getZSpan() - 1;
//                }

                if (missedChunks.computeIfAbsent(structureRegionLong, (value) -> new LongArraySet()).contains(currentChunk)) {
                    continue;
                }

                ChunkPos chunkPos = village.getPotentialFeatureChunk(structureSeperationSettings, seed, new SharedSeedRandom(), structureChunkPos.x, structureChunkPos.z);

                if (chunkPos.x == structureChunkPos.x && chunkPos.z == structureChunkPos.z && sampleAndTestChunkBiomesForStructure(structureChunkPos.x, structureChunkPos.z, biomeSource, village)) {
                    regionPositions.computeIfAbsent(structureRegionLong, (value) -> new LongArraySet()).add(currentChunk);
                } else {
                    if (missedChunks.computeIfAbsent(structureRegionLong, (value) -> new LongArraySet()).size() > 5000) {
                        missedChunks.clear();
                    }

                    missedChunks.computeIfAbsent(structureRegionLong, (value) -> new LongArraySet()).add(currentChunk);
                }
            }
        }
        this.searchedBox.computeIfAbsent(serverLevel, (level2) -> new MutableBoundingBox()).expand(new MutableBoundingBox(minGridX, 0, minGridZ, maxGridX, 0, maxGridZ));
    }


    /**
     * If the node from the Path generator intersects the current chunk, generate.
     */
    private void generateForNode(ISeedReader worldRegion, StartEndPathGenerator.Node node) {
        BlockPos.Mutable mutable = new BlockPos.Mutable().set(node.getPos());
        mutable.setY(worldRegion.getHeight(Heightmap.Type.WORLD_SURFACE_WG, mutable.getX(), mutable.getZ()) - 1);

        for (int height = 0; height < 25; height++) {
            worldRegion.setBlock(mutable.move(Direction.UP), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
        }
    }

    private void addPathGenerators(ISeedReader worldRegion, ChunkGenerator generator, Random rand, long seed, Long2ReferenceOpenHashMap<LongSet> regionPositions, Long2LongArrayMap successPairPositionsLookup, Long2ReferenceOpenHashMap<ArrayList<StartEndPathGenerator>> regionPathGenerators, Long2IntArrayMap structureChunkPosToConnectedPathCount, StructureSeparationSettings structureSeperationSettings, long regionLong, LongSet regionStructurePositions) {
        if (regionStructurePositions.size() < 2) {
            return;
        }

        long[] structurePositionsForRegion = regionStructurePositions.toLongArray();

        long startStructurePos = structurePositionsForRegion[rand.nextInt(structurePositionsForRegion.length - 1)];
        int connectPathCountForStructure = structureChunkPosToConnectedPathCount.computeIfAbsent(startStructurePos, structurePos -> 0);

        long endStructurePos = structurePositionsForRegion[rand.nextInt(structurePositionsForRegion.length - 1)];

        while (structurePositionsForRegion.length - 1 > 2 && (startStructurePos == endStructurePos) ||
                (successPairPositionsLookup.containsKey(endStructurePos) && successPairPositionsLookup.get(endStructurePos) == startStructurePos) ||
                (successPairPositionsLookup.containsKey(startStructurePos) && successPairPositionsLookup.get(startStructurePos) == endStructurePos)) {

            endStructurePos = structurePositionsForRegion[rand.nextInt(structurePositionsForRegion.length - 1)];
            ;
        }

        int pathCountEndStructurePos = structureChunkPosToConnectedPathCount.computeIfAbsent(endStructurePos, structurePos -> 0);

        if (connectPathCountForStructure > 2) {
            startStructurePos = acquireNewStructurePos(rand, regionPositions, regionStructurePositions, startStructurePos);
        }

        if (pathCountEndStructurePos > 2) {
            endStructurePos = acquireNewStructurePos(rand, regionPositions, regionStructurePositions, endStructurePos);
        }

        int startX = ChunkPos.getX(startStructurePos);
        int startZ = ChunkPos.getZ(startStructurePos);
        BlockPos startPos = new BlockPos(SectionPos.sectionToBlockCoord(startX), 0, SectionPos.sectionToBlockCoord(startZ));

        int endX = ChunkPos.getX(endStructurePos);
        int endZ = ChunkPos.getZ(endStructurePos);
        BlockPos endPos = new BlockPos(SectionPos.sectionToBlockCoord(endX), 0, SectionPos.sectionToBlockCoord(endZ));

        SharedSeedRandom random = new SharedSeedRandom();

        random.setLargeFeatureWithSalt(seed, Math.floorDiv(startX + 1, endX + 1), Math.floorDiv(startZ + 1, endZ + 1), structureSeperationSettings.salt());

        noise.SetSeed(random.nextInt());

        addPathGenerator(worldRegion, generator, successPairPositionsLookup, regionPathGenerators, structureChunkPosToConnectedPathCount, regionLong, startStructurePos, connectPathCountForStructure, endStructurePos, pathCountEndStructurePos, startPos, endPos);
    }


    /**
     * Add a path generator to this region's cache and cache how many times a given position has succeeded.
     */
    private void addPathGenerator(ISeedReader worldRegion, ChunkGenerator generator, Long2LongArrayMap successPairPositionsLookup, Long2ReferenceOpenHashMap<ArrayList<StartEndPathGenerator>> regionPathGenerators, Long2IntArrayMap structureChunkPosToConnectedPathCount, long regionLong, long startStructurePos, int connectPathCountForStructure, long endStructurePos, int pathCountEndStructurePos, BlockPos startPos, BlockPos endPos) {
        StartEndPathGenerator startEndPathGenerator = new StartEndPathGenerator(noise, worldRegion, startPos, endPos, generator, (node -> isNodeInvalid(node, worldRegion, pathBox(startStructurePos, endStructurePos))), node -> {
            BlockPos nodePos = node.getPos();
            int nodeChunkX = SectionPos.blockToSectionCoord(nodePos.getX());
            int nodeChunkZ = SectionPos.blockToSectionCoord(nodePos.getZ());

            return nodeChunkX == SectionPos.blockToSectionCoord(endPos.getX()) && nodeChunkZ == SectionPos.blockToSectionCoord(endPos.getZ());

        }, 500000);

        if (startEndPathGenerator.exists()) {
            System.out.println(startPos.toString() + ", " + endPos.toString());
            successPairPositionsLookup.put(startStructurePos, endStructurePos);

            regionPathGenerators.computeIfAbsent(regionLong, (long2) -> new ArrayList<>()).add(startEndPathGenerator);

            structureChunkPosToConnectedPathCount.put(startStructurePos, connectPathCountForStructure + 1);
            structureChunkPosToConnectedPathCount.put(endStructurePos, pathCountEndStructurePos + 1);
        }
    }

    /**
     * Returning true tells the Path generator to recompute the angle of the pos used to create this position w/ a different angle.
     */
    private boolean isNodeInvalid(StartEndPathGenerator.Node node, ISeedReader world, MutableBoundingBox pathBox) {
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

    private long acquireNewStructurePos(Random rand, Long2ReferenceOpenHashMap<LongSet> regionPositions, LongSet regionStructurePositions, long structurePos) {
        regionStructurePositions.remove(structurePos);
        long[] structurePositionsForRegion = regionStructurePositions.toLongArray();
        structurePos = structurePositionsForRegion[rand.nextInt(structurePositionsForRegion.length - 1)];
        return structurePos;
    }

    public static void setupNoise(long serverSeed) {
        if (seed != serverSeed || noise == null) {
            seed = serverSeed;
            noise = new FastNoise((int) seed);
            noise.SetNoiseType(FastNoise.NoiseType.PerlinFractal);
            noise.SetGradientPerturbAmp(1);
            noise.SetFractalOctaves(5);
            noise.SetFractalGain(0.5f);
            noise.SetFrequency(0.08F / 5);
        }
    }

    private MutableBoundingBox pathBox(long startPos, long endPos) {
        int startPosChunkX = ChunkPos.getX(startPos);
        int startPosChunkZ = ChunkPos.getZ(startPos);

        int endPosChunkX = ChunkPos.getX(endPos);
        int endPosChunkZ = ChunkPos.getZ(endPos);

        int xDiff = Math.abs(startPosChunkX - endPosChunkX);
        int zDiff = Math.abs(startPosChunkZ - endPosChunkZ);


        if (xDiff > zDiff) {
            return new MutableBoundingBox(startPosChunkX, 0, startPosChunkZ - 4, endPosChunkX, 0, endPosChunkZ + 3);
        } else if (zDiff > xDiff) {
            return new MutableBoundingBox(startPosChunkX - 4, 0, startPosChunkZ, endPosChunkX + 3, 0, endPosChunkZ);
        } else {
            return new MutableBoundingBox(startPosChunkX, 0, startPosChunkZ, endPosChunkX, 0, endPosChunkZ);
        }
    }

    public boolean sampleAndTestChunkBiomesForStructure(int chunkX, int chunkZ, BiomeManager.IBiomeReader biomeReader, Structure<?> structure) {
        return biomeReader.getNoiseBiome((chunkX << 2) + 2, 0, (chunkZ << 2) + 2).getGenerationSettings().isValidStart(structure);
    }


    private ChunkPos getStructureChunkPos(Structure<?> structure, long worldSeed, int structureSalt, SharedSeedRandom seedRandom, int spacing, int separation, int floorDivX, int floorDivZ) {
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
        return new ChunkPos(floorDivX * spacing + x, floorDivZ * spacing + z);
    }
}
