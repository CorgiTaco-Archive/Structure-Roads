package corgitaco.modid.river;

import com.mojang.serialization.Codec;
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
import net.minecraft.world.server.ServerWorld;

import java.util.Map;
import java.util.Random;

public class WorldPathGenerator extends Feature<NoFeatureConfig> {

    public static final Feature<NoFeatureConfig> PATH = BiomeUtils.createFeature("path", new WorldPathGenerator(NoFeatureConfig.CODEC));

    public static final ConfiguredFeature<?, ?> CONFIGURED_PATH = BiomeUtils.createConfiguredFeature("path", PATH.configured(NoFeatureConfig.INSTANCE).decorated(Placement.NOPE.configured(NoPlacementConfig.INSTANCE)));

    public WorldPathGenerator(Codec<NoFeatureConfig> codec) {
        super(codec);
    }

    private static long seed;

    public static FastNoise noise;


    Map<World, PathGenerator> cache = new Object2ObjectArrayMap<>();

    Map<World, LongSet> missedChunks = new Object2ObjectArrayMap<>();


    @Override
    public boolean place(ISeedReader worldRegion, ChunkGenerator generator, Random rand, BlockPos pos, NoFeatureConfig config) {
        long seed = worldRegion.getSeed();
        setupNoise(seed);

        LongSet missedChunks = this.missedChunks.computeIfAbsent(worldRegion.getLevel(), (level) -> new LongArraySet());

        int searchRange = 5;
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());

        int xSeed = chunkX >> 4;
        int zSeed = chunkZ >> 4;
        SharedSeedRandom structureRandom = new SharedSeedRandom();
        structureRandom.setSeed((long) (xSeed ^ zSeed << 4) ^ seed);
        BiomeProvider biomeSource = generator.getBiomeSource();

        Structure<VillageConfig> village = Structure.VILLAGE;

        ServerWorld level = worldRegion.getLevel();
        if (!cache.containsKey(level)) {
            for (int chunkXSearch = -searchRange; chunkXSearch < searchRange; chunkXSearch++) {
                for (int chunkZSearch = -searchRange; chunkZSearch < searchRange; chunkZSearch++) {
                    int currentChunkX = chunkX + chunkXSearch;
                    int currentChunkZ = chunkZ + chunkZSearch;
                    long currentChunk = ChunkPos.asLong(currentChunkX, currentChunkZ);

                    if (missedChunks.contains(currentChunk)) {
                        continue;
                    }

                    ChunkPos chunkPos = village.getPotentialFeatureChunk(generator.getSettings().structureConfig().get(village), seed, structureRandom, currentChunkX, currentChunkZ);

                    if (chunkPos.x == currentChunkX && chunkPos.z == currentChunkZ && sampleAndTestChunkBiomesForStructure(currentChunkX, 5, currentChunkZ, biomeSource, village)) {
                        int blockX = SectionPos.sectionToBlockCoord(chunkPos.x);
                        int blockZ = SectionPos.sectionToBlockCoord(chunkPos.z);
                        BlockPos pos1 = new BlockPos(blockX, generator.getBaseHeight(blockX, blockZ, Heightmap.Type.WORLD_SURFACE_WG) + 1, blockZ);
                        cache.put(level, new PathGenerator(noise, worldRegion, pos1, generator, blockPos -> false, nodePos -> {
                            int nodeChunkX = SectionPos.blockToSectionCoord(nodePos.getX());
                            int nodeChunkZ = SectionPos.sectionToBlockCoord(nodePos.getZ());
                            ChunkPos foundPotentialFeatureChunk = village.getPotentialFeatureChunk(generator.getSettings().structureConfig().get(village), seed, structureRandom, nodeChunkX, nodeChunkZ);
                            return foundPotentialFeatureChunk.x == nodeChunkX && foundPotentialFeatureChunk.z == nodeChunkZ && sampleAndTestChunkBiomesForStructure(nodeChunkX, 0, nodeChunkZ, biomeSource, village);
                        }, 2500));
                    } else {
                        if (missedChunks.size() > 5000) {
                            missedChunks.clear();
                        }

                        missedChunks.add(currentChunk);
                    }
                }
                if (cache.containsKey(level)) {
                    break;
                }
            }
        }

        if (!cache.containsKey(level)) {
            return false;
        }

        PathGenerator pathGenerator = cache.get(level);

        if (!pathGenerator.exists()) {
            cache.remove(level);
            return false;
        }

        long key = ChunkPos.asLong(chunkX, chunkZ);

        if (pathGenerator.getNodeChunkPositions().contains(key)) {
            BlockPos.Mutable mutable = new BlockPos.Mutable();
            for (PathGenerator.Node node : pathGenerator.getNodesForChunk(key)) {
                BlockPos.Mutable nodePos = node.getPos();
                mutable.set(nodePos);
                int worldRegionHeight = worldRegion.getHeight(Heightmap.Type.WORLD_SURFACE_WG, nodePos.getX(), nodePos.getZ());
                node.setHeightAtLocation(worldRegionHeight);
                mutable.setY(worldRegionHeight);
                nodePos.setY(worldRegionHeight);
                for (int height = 0; height < 25; height++) {
                    worldRegion.setBlock(mutable.move(Direction.UP), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
                }
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

    public boolean sampleAndTestChunkBiomesForStructure(int chunkX, int y, int chunkZ, BiomeManager.IBiomeReader biomeReader, Structure<?> structure) {
        int blockX = SectionPos.sectionToBlockCoord(chunkX);
        int blockZ = SectionPos.sectionToBlockCoord(chunkZ);

        for (int x = blockX; x < blockX + 16; x += 4) {
            for (int z = blockZ; z < blockZ + 16; z += 4) {
                if (biomeReader.getNoiseBiome(x, y, z).getGenerationSettings().isValidStart(structure)) {
                    return true;
                }
            }
        }
        return false;
    }
}
