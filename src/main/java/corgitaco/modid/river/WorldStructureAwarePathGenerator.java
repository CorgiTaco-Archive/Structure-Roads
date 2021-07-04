package corgitaco.modid.river;

import com.mojang.serialization.Codec;
import corgitaco.modid.mixin.access.StructureAccess;
import corgitaco.modid.util.BiomeUtils;
import corgitaco.modid.util.fastnoise.FastNoise;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
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

    private final Map<World, LongSet> missedChunks = new Object2ObjectArrayMap<>();
    private final Map<World, LongSet> structurePositions = new Object2ObjectArrayMap<>();
    private final Map<World, MutableBoundingBox> searchedBox = new Object2ObjectArrayMap<>();

    @Override
    public boolean place(ISeedReader worldRegion, ChunkGenerator generator, Random rand, BlockPos pos, NoFeatureConfig config) {
        long seed = worldRegion.getSeed();
        setupNoise(seed);

        ServerWorld serverLevel = worldRegion.getLevel();
        LongSet missedChunks = this.missedChunks.computeIfAbsent(serverLevel, (level) -> new LongArraySet());
        LongSet structurePositions = this.structurePositions.computeIfAbsent(serverLevel, (level1) -> new LongArraySet());

        int searchRange = 1000;
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());

        BiomeProvider biomeSource = generator.getBiomeSource();

        Structure<VillageConfig> village = Structure.VILLAGE;

        StructureSeparationSettings structureSeperationSettings = generator.getSettings().structureConfig().get(village);
        int spacing = structureSeperationSettings.spacing();

        int currentGridX = Math.floorDiv(chunkX, spacing);
        int currentGridZ = Math.floorDiv(chunkZ, spacing);

        int actualMinGridX = Math.floorDiv(chunkX - searchRange, spacing);
        int actualMinGridZ = Math.floorDiv(chunkZ - searchRange, spacing);
        int actualMaxGridX = Math.floorDiv(chunkX + searchRange, spacing);
        int actualMaxGridZ = Math.floorDiv(chunkZ + searchRange, spacing);

        MutableBoundingBox searchedRange = this.searchedBox.get(serverLevel);

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
                long currentChunk = ChunkPos.asLong(structureChunkPos.x, structureChunkPos.z);

                if (searchedRange != null && searchedRange.intersects(structureGridX, structureGridZ, structureGridX, structureGridZ)) {
                    structureGridX = minGridX + searchedRange.getXSpan() - 1;
                    structureGridZ = minGridZ + searchedRange.getZSpan() - 1;
                }

                if (missedChunks.contains(currentChunk)) {
                    continue;
                }

                ChunkPos chunkPos = village.getPotentialFeatureChunk(structureSeperationSettings, seed, new SharedSeedRandom(), structureChunkPos.x, structureChunkPos.z);

                if (chunkPos.x == structureChunkPos.x && chunkPos.z == structureChunkPos.z && sampleAndTestChunkBiomesForStructure(structureChunkPos.x, structureChunkPos.z, biomeSource, village)) {
                    structurePositions.add(currentChunk);
                } else {
                    if (missedChunks.size() > 5000) {
                        missedChunks.clear();
                    }

                    missedChunks.add(currentChunk);
                }
            }
        }
        this.searchedBox.computeIfAbsent(serverLevel, (level2) -> new MutableBoundingBox()).expand(new MutableBoundingBox(minGridX, 0, minGridZ, maxGridX, 0, maxGridZ));

        long key = ChunkPos.asLong(chunkX, chunkZ);

        if (this.structurePositions.get(serverLevel).contains(key)) {
            BlockPos.Mutable mutable = new BlockPos.Mutable().set(pos);

            mutable.setY(worldRegion.getHeight(Heightmap.Type.WORLD_SURFACE_WG, mutable.getX(), mutable.getZ()) - 1);
            for (int height = 0; height < 25; height++) {
                worldRegion.setBlock(mutable.move(Direction.UP), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
            }
        }
        return true;
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

    private boolean inRange(int chunkX, int chunkZ, int structureX, int structureZ, int radius) {
        return Math.abs(chunkX - structureX) <= radius && Math.abs(chunkZ - structureZ) <= radius;
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
