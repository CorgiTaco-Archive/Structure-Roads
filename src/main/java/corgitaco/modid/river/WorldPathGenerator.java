package corgitaco.modid.river;

import com.mojang.serialization.Codec;
import corgitaco.modid.util.BiomeUtils;
import corgitaco.modid.util.fastnoise.FastNoise;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.block.BlockState;
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
import net.minecraft.world.gen.settings.StructureSeparationSettings;
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
    Map<World, LongSet> sampled = new Object2ObjectArrayMap<>();
    Map<World, LongSet> structurePositions = new Object2ObjectArrayMap<>();

    @Override
    public boolean place(ISeedReader worldRegion, ChunkGenerator generator, Random rand, BlockPos pos, NoFeatureConfig config) {
        long seed = worldRegion.getSeed();
        setupNoise(seed);

        LongSet missedChunks = this.missedChunks.computeIfAbsent(worldRegion.getLevel(), (level) -> new LongArraySet());

        int searchRange = 100;
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());

        int xSeed = chunkX >> 4;
        int zSeed = chunkZ >> 4;
        //        structureRandom.setSeed((long) (xSeed ^ zSeed << 4) ^ seed);
        BiomeProvider biomeSource = generator.getBiomeSource();

        Structure<VillageConfig> village = Structure.VILLAGE;

        ServerWorld level = worldRegion.getLevel();
        LongSet sampled = this.sampled.computeIfAbsent(level, (level1) -> new LongArraySet());

        if (!cache.containsKey(level)) {
            for (int chunkXSearch = -searchRange; chunkXSearch < searchRange; chunkXSearch++) {
                for (int chunkZSearch = -searchRange; chunkZSearch < searchRange; chunkZSearch++) {
                    int currentChunkX = chunkX + chunkXSearch;
                    int currentChunkZ = chunkZ + chunkZSearch;
                    long currentChunk = ChunkPos.asLong(currentChunkX, currentChunkZ);

                    if (missedChunks.contains(currentChunk)) {
                        continue;
                    }

                    StructureSeparationSettings structureSeperationSettings = generator.getSettings().structureConfig().get(village);
                    ChunkPos chunkPos = village.getPotentialFeatureChunk(structureSeperationSettings, seed, new SharedSeedRandom(), currentChunkX, currentChunkZ);

                    if (chunkPos.x == currentChunkX && chunkPos.z == currentChunkZ && sampleAndTestChunkBiomesForStructure(currentChunkX, currentChunkZ, biomeSource, village)) {
                        int blockX = SectionPos.sectionToBlockCoord(chunkPos.x);
                        int blockZ = SectionPos.sectionToBlockCoord(chunkPos.z);
                        BlockPos pos1 = new BlockPos(blockX, generator.getBaseHeight(blockX, blockZ, Heightmap.Type.WORLD_SURFACE_WG) + 1, blockZ);
                        cache.put(level, new PathGenerator(noise, worldRegion, pos1, generator, blockPos -> false, nodePos -> {
                            int nodeChunkX = SectionPos.blockToSectionCoord(nodePos.getX());
                            int nodeChunkZ = SectionPos.blockToSectionCoord(nodePos.getZ());
                            long currentNodeChunk = ChunkPos.asLong(nodeChunkX, nodeChunkZ);

                            int range = 8;
                            if (sampled.contains(currentNodeChunk) || inRange(nodeChunkX, nodeChunkZ, chunkPos.x, chunkPos.z, range)) {
                                return false;
                            }

                            sampled.add(currentNodeChunk);

                            SharedSeedRandom sharedSeedRandom = new SharedSeedRandom();
//                            sharedSeedRandom.setSeed((long)((nodeChunkX >> 4) ^ (nodeChunkZ >> 4) << 4) ^ seed);
//                            if (sharedSeedRandom.nextInt(5) != 0) {
//                                return false;
//                            }

                            ChunkPos foundPotentialFeatureChunk = village.getPotentialFeatureChunk(structureSeperationSettings, seed, sharedSeedRandom, nodeChunkX, nodeChunkZ);
                            return foundPotentialFeatureChunk.x == nodeChunkX && foundPotentialFeatureChunk.z == nodeChunkZ && sampleAndTestChunkBiomesForStructure(nodeChunkX, nodeChunkZ, biomeSource, village);
                        }, 10000));
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

                BlockState state = node.getIdx() == 0 ? Blocks.EMERALD_BLOCK.defaultBlockState() : node.getIdx() == pathGenerator.getTotalNumberOfNodes() ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.DIAMOND_BLOCK.defaultBlockState();

                for (int height = 0; height < 25; height++) {
                    worldRegion.setBlock(mutable.move(Direction.UP), state, 2);
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

    private boolean inRange(int chunkX, int chunkZ, int structureX, int structureZ, int radius) {
        return Math.abs(chunkX - structureX) <= radius && Math.abs(chunkZ - structureZ) <= radius;
    }


    public boolean sampleAndTestChunkBiomesForStructure(int chunkX, int chunkZ, BiomeManager.IBiomeReader biomeReader, Structure<?> structure) {
        return biomeReader.getNoiseBiome((chunkX << 2) + 2, 0, (chunkZ << 2) + 2).getGenerationSettings().isValidStart(structure);
    }
}
