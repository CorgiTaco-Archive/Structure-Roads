package corgitaco.modid.river;

import corgitaco.modid.util.fastnoise.FastNoise;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Used to dynamically create a randomly generated path from 1 object to another.
 */
public class StartEndPathGenerator {
    private final List<Node> nodes;
    private final Long2ObjectArrayMap<List<Node>> fastNodes;

    private static float minNoise = 100000;
    private static float maxNoise = -292929292;

    public StartEndPathGenerator(FastNoise noise, ISeedReader world, BlockPos startPos, BlockPos endPos, ChunkGenerator generator, Predicate<Node> isInvalid, Predicate<Node> isValid, int maxDistance) {
        List<Node> nodes = new ArrayList<>();
        Long2ObjectArrayMap<List<Node>> fastNodes = new Long2ObjectArrayMap<>();

        nodes.add(new Node(startPos.mutable(), 0));
        int distanceInNodes = maxDistance / 5;


        for (int i = 1; i < distanceInNodes; i++) {
            Node prevNode = nodes.get(i - 1);
            float angle = noise.GetNoise(prevNode.getPos().getX(), 0, prevNode.getPos().getZ());
            if (angle < minNoise) {
                minNoise = angle;
                System.out.println("Min noise: " + angle);
            }

            if (angle > maxNoise) {
                maxNoise = angle;
                System.out.println("Max noise: " + angle);
            }

            float noiseAngle = angle * 5;


            BlockPos.Mutable pos = getNextPosAngled(prevNode, noiseAngle);
            Node nextNode = new Node(pos, i);

            // 0.5F is the equivalent of 30 degrees in this case
            double degreesRotated = Math.PI / 12;
            while (isInvalid.test(nextNode)) {
                Vector3i angleOffset = getAngleOffset((float) (noiseAngle + degreesRotated));
                degreesRotated += Math.PI / 12;
                nextNode.getPos().setWithOffset(prevNode.getPos(), angleOffset.getX(), 0, angleOffset.getZ());

                if (degreesRotated >= Math.PI) {
                    this.nodes = null;
                    this.fastNodes = null;
                    return; // This should never ever hit.
                }
            }


            long key = ChunkPos.asLong(SectionPos.blockToSectionCoord(nextNode.getPos().getX()), SectionPos.blockToSectionCoord(nextNode.getPos().getZ()));

            if (isValid.test(nextNode)) {
                nodes.add(nextNode);
                fastNodes.computeIfAbsent(key, key2 -> new ArrayList<>()).add(nextNode);

                this.nodes = nodes;
                this.fastNodes = fastNodes;
                return;
            }
            nodes.add(nextNode);
            fastNodes.computeIfAbsent(key, key2 -> new ArrayList<>()).add(nextNode);
        }
        this.nodes = null;
        this.fastNodes = null;
    }


    private BlockPos.Mutable getNextPosAngled(Node prevNode, float angle) {
//        System.out.println(angle);
        BlockPos previousNodePos = prevNode.getPos();
        BlockPos offset = getAngleOffsetPos(angle, previousNodePos);
        return new BlockPos.Mutable().set(offset);
    }

    private BlockPos getAngleOffsetPos(float angle, BlockPos previousNodePos) {
        Vector3i vecAngle = getAngleOffset(angle);
        return previousNodePos.offset(vecAngle);
    }

    // Angle -1.5 = West(Negative X)
    // Angle 0 = South(Positive Z)
    // Angle 1.5 = East(Positive X)
    // Angle 3 = North(Negative Z)
    private Vector3i getAngleOffset(float angle) {
        Vector2f dAngle = get2DAngle(angle, 5);
        return new Vector3i(dAngle.x, 0, dAngle.y);
    }

    public static Vector2f get2DAngle(float angle, float length) {
        float x = (float) (Math.sin(angle) * length);
        float y = (float) (Math.cos(angle) * length);

        return new Vector2f(x, y);
    }

    public boolean exists() {
        return nodes != null;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Node> getNodesForChunk(long pos) {
        return this.fastNodes.get(pos);
    }

    public LongSet getNodeChunkPositions() {
        return this.fastNodes.keySet();
    }

    public BlockPos getFinalPosition() {
        return this.nodes.get(this.nodes.size() - 1).getPos();
    }

    public int getTotalNumberOfNodes() {
        return this.nodes.size();
    }

    public BlockPos getStartPos() {
        return this.nodes.get(0).getPos();
    }

    static class Node {

        private final int idx;
        private final BlockPos.Mutable pos;
        private int heightAtLocation = 0;

        private Node(BlockPos.Mutable pos, int idx) {
            this.pos = pos;
            this.idx = idx;
        }

        public BlockPos.Mutable getPos() {
            return pos;
        }

        public int getIdx() {
            return idx;
        }

        public void setHeightAtLocation(int heightAtLocation) {
            this.heightAtLocation = heightAtLocation;
        }

        public int getHeightAtLocation(ChunkGenerator generator) {
            if (heightAtLocation == 0) {
                return generator.getBaseHeight(pos.getX(), pos.getZ(), Heightmap.Type.WORLD_SURFACE_WG);
            }

            return heightAtLocation;
        }
    }
}
