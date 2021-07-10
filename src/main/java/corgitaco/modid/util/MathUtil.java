package corgitaco.modid.util;

import net.minecraft.util.math.BlockPos;

public class MathUtil {
    public static double angle(BlockPos startPos, BlockPos endPos) {
        return Math.atan2(endPos.getX() - startPos.getX(), endPos.getZ() - startPos.getZ());
    }

    public static boolean inRange(int chunkX, int chunkZ, int structureX, int structureZ, int radius) {
        return Math.abs(chunkX - structureX) <= radius && Math.abs(chunkZ - structureZ) <= radius;
    }
}
