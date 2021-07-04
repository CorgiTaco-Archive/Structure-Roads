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
public class PathGenerator {
    private final List<Node> nodes;
    private final Long2ObjectArrayMap<List<Node>> fastNodes;

    public PathGenerator(FastNoise noise, ISeedReader world, BlockPos startPos, ChunkGenerator generator, Predicate<BlockPos> isInvalid, Predicate<BlockPos> isValid, int maxDistance) {
        List<Node> nodes = new ArrayList<>();
        Long2ObjectArrayMap<List<Node>> fastNodes = new Long2ObjectArrayMap<>();


        nodes.add(new Node(startPos.mutable(), 0));
        int distanceInNodes = maxDistance / 5;

        int startY = startPos.getY();

        for (int i = 1; i < distanceInNodes; i++) {
            Node prevNode = nodes.get(i - 1);
            float angle = noise.GetNoise(prevNode.getPos().getX(), 0, prevNode.getPos().getZ());

            Vector2f dAngle = get2DAngle(angle * 5, 5);
            BlockPos previousNodePos = prevNode.getPos();
            Vector3i vecAngle = new Vector3i(dAngle.x, 0, dAngle.y);

            BlockPos addedPos = previousNodePos.offset(vecAngle);
            int newY = 0;  //generator.getFirstFreeHeight(addedPos.getX(), addedPos.getZ(), Heightmap.Type.OCEAN_FLOOR_WG);

//            if (newY > previousNodePos.getY()) {
//                newY = previousNodePos.getY();
//            }
//
//            if (newY < generator.getSeaLevel() + 1) {
//                newY = generator.getSeaLevel() + 1;
//            }

            BlockPos.Mutable pos = new BlockPos.Mutable(addedPos.getX(), newY, addedPos.getZ());


            Node nextNode = new Node(pos, i);

            if (isInvalid.test(nextNode.getPos())) {
                break;
            }
            long key = ChunkPos.asLong(SectionPos.blockToSectionCoord(nextNode.getPos().getX()), SectionPos.blockToSectionCoord(nextNode.getPos().getZ()));

            if (isValid.test(nextNode.getPos())) {
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

    public static Vector2f get2DAngle(float angle, float length) {
        float x = (float) (Math.sin(angle) * length);
        float y = (float) (Math.cos(angle) * length);

        return new Vector2f(x, y);
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
