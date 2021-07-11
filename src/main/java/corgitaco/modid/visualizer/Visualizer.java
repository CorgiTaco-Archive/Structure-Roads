package corgitaco.modid.visualizer;

import corgitaco.modid.river.perlin.WarpedStartEndGenerator;
import corgitaco.modid.util.fastnoise.FastNoise;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import static corgitaco.modid.util.MathUtil.inRange;

public class Visualizer {

    public static void main(String[] args) {
        int seed = 128374575;
        Random random = new Random(seed);
        int range = 1000;
        BufferedImage img = new BufferedImage(range, range, BufferedImage.TYPE_INT_RGB);


        BlockPos startPos = new BlockPos(random.nextInt(range - 25), 0, random.nextInt(range - 25));
        int startX = SectionPos.blockToSectionCoord(startPos.getX());
        int startZ = SectionPos.blockToSectionCoord(startPos.getZ());
        long startStructurePos = ChunkPos.asLong(startX, startZ);

        BlockPos endPos = new BlockPos(random.nextInt(range - 25), 0, random.nextInt(range - 25));

        int endX = SectionPos.blockToSectionCoord(endPos.getX());
        int endZ = SectionPos.blockToSectionCoord(endPos.getZ());
        long endStructurePos = ChunkPos.asLong(endX, endZ);

        FastNoise noise = createNoise(seed);
        WarpedStartEndGenerator warpedStartEndGenerator = new WarpedStartEndGenerator(noise, random, startPos, endPos, (node -> false), (node) -> {
            BlockPos nodePos = node.getPos();
            int nodeChunkX = SectionPos.blockToSectionCoord(nodePos.getX());
            int nodeChunkZ = SectionPos.blockToSectionCoord(nodePos.getZ());
            long nodeChunk = ChunkPos.asLong(nodeChunkX, nodeChunkZ);

            return inRange(nodeChunkX, nodeChunkZ, ChunkPos.getX(endStructurePos), ChunkPos.getZ(endStructurePos), 0);
        }, range, 0, 5);



        String pathname = "run\\yeet.png";
        File file = new File(pathname);
        if (file.exists())
            file.delete();

        for (int x = 0; x < range; x++) {
            for (int z = 0; z < range; z++) {
                img.setRGB(x, z, getColor(x, z, startX, startZ, endX, endZ));
            }
        }
        int rgb = new Color(24, 154, 25).getRGB();
        for (WarpedStartEndGenerator.Node node : warpedStartEndGenerator.getNodes()) {
            int x = node.getPos().getX();
            int z = node.getPos().getZ();


            int size = 1;
            for (int xMove = -size; xMove < size; xMove++) {
                for (int zMove = -size; zMove < size; zMove++) {
                    int xCoord = x + xMove;
                    int zCoord = z + zMove;
                    if (xCoord < range && zCoord < range && xCoord > 0 && zCoord > 0) {
                        img.setRGB((xCoord), zCoord, rgb);
                    }
                }
            }
        }



        try {
            file = new File(pathname);
            ImageIO.write(img, "png", file);
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    public static int getColor(int x, int z, int startX, int startZ, int endX, int endZ) {
        if (SectionPos.blockToSectionCoord(x) == startX && SectionPos.blockToSectionCoord(z) == startZ) {
            return new Color(0, 0, 255).getRGB();
        } else if (SectionPos.blockToSectionCoord(x) == endX && SectionPos.blockToSectionCoord(z) == endZ) {
            return new Color(0, 255, 255).getRGB();
        } else {
            return 0;
        }
    }

    public static FastNoise createNoise(int seed) {
        FastNoise noise = new FastNoise(seed);
        noise.SetNoiseType(FastNoise.NoiseType.OpenSimplex2S);
        noise.SetFrequency(0.00000000000000003455F);
        noise.SetFractalGain(5);

        return noise;
    }
}