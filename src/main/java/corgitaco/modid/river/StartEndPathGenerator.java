package corgitaco.modid.river;

import corgitaco.modid.util.fastnoise.FastNoise;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3i;
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
    private final FastNoise noise;
    private final BlockPos startPos;
    private final BlockPos endPos;
    private final int distanceBetweenNodes;

    public static final double DEGREE_ROTATION = Math.PI / 3;

    public static final boolean RETURN_BROKEN_GENERATORS = false;


    public StartEndPathGenerator(List<Node> nodes, Long2ObjectArrayMap<List<Node>> fastNodes, FastNoise noise, BlockPos startPos, BlockPos endPos, int distanceBetweenNodes) {
        this.nodes = nodes;
        this.fastNodes = fastNodes;
        this.noise = noise;
        this.startPos = startPos;
        this.endPos = endPos;
        this.distanceBetweenNodes = distanceBetweenNodes;
    }

    public StartEndPathGenerator(FastNoise noise, BlockPos startPos, BlockPos endPos, Predicate<Node> isInvalid, Predicate<Node> isValid, int maxDistance, float generatorRotation, int distanceBetweenNodes) {
        this.noise = noise;
        this.startPos = startPos;
        this.endPos = endPos;
        this.distanceBetweenNodes = distanceBetweenNodes;
        List<Node> nodes = new ArrayList<>();
        Long2ObjectArrayMap<List<Node>> fastNodes = new Long2ObjectArrayMap<>();

        nodes.add(new Node(startPos.mutable(), 0));
        int distanceInNodes = maxDistance / distanceBetweenNodes;


        for (int i = 1; i < distanceInNodes; i++) {
            Node prevNode = nodes.get(i - 1);
            BlockPos.Mutable prevPos = prevNode.getPos();
            float angle = noise.GetNoise(prevPos.getX(), 0, prevPos.getZ());
            float noiseAngle = angle * 5;

            Vector3i angleOffset = getAngleOffset(noiseAngle);
            BlockPos.Mutable pos = new BlockPos.Mutable(prevPos.getX() + angleOffset.getX(), prevPos.getY() + angleOffset.getY(), prevPos.getZ() + angleOffset.getZ());
            Node nextNode = new Node(pos, i);
            long key = ChunkPos.asLong(SectionPos.blockToSectionCoord(nextNode.getPos().getX()), SectionPos.blockToSectionCoord(nextNode.getPos().getZ()));

            List<BlockPos> failedPositions = new ArrayList<>();
            double degreesRotated = DEGREE_ROTATION;
            while (isInvalid.test(nextNode)) {
                Vector3i rotatedAngleOffset = getAngleOffset((float) (noiseAngle + degreesRotated));
                degreesRotated += DEGREE_ROTATION;
                BlockPos failedPos = new BlockPos(nextNode.getPos());
                nextNode.getPos().set(prevPos.getX() + rotatedAngleOffset.getX(), 0, prevPos.getZ() + rotatedAngleOffset.getZ());
                nextNode.setAngleOffset((float) degreesRotated);

                failedPositions.add(failedPos);

                if (degreesRotated >= Math.PI * 2) {
                    if (RETURN_BROKEN_GENERATORS) {
                        nextNode.getFailedPositions().addAll(failedPositions);
                        nodes.add(nextNode);
                        fastNodes.computeIfAbsent(key, key2 -> new ArrayList<>()).add(nextNode);
                        this.nodes = nodes;
                        this.fastNodes = fastNodes;
                        return; // This should never ever hit.
                    } else {
                        this.nodes = null;
                        this.fastNodes = null;
                        return;
                    }
                }
            }



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

    public CompoundNBT write() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putInt("seed", this.noise.GetSeed());
        nbt.putInt("distanceBetweenNodes", this.distanceBetweenNodes);
        nbt.putIntArray("startPos", writeBlockPos(this.startPos));
        nbt.putIntArray("endPos", writeBlockPos(this.endPos));

        ListNBT fastNodes = new ListNBT();
        this.fastNodes.forEach((key, blockPosList) -> {
            CompoundNBT tag = new CompoundNBT();
            tag.putLong("chunkPos", key);
            ListNBT nodes = new ListNBT();
            for (Node node : blockPosList) {
                CompoundNBT nodeTag = new CompoundNBT();
                nodeTag.putIntArray("pos", writeBlockPos(node.getPos()));
                nodeTag.putInt("idx", node.getIdx());
                nodeTag.putInt("generated", node.getGeneratedForNode());
                nodeTag.putFloat("angleOffset", node.getAngleOffset());
                nodes.add(nodeTag);
            }
            tag.put("nodes", nodes);
            fastNodes.add(tag);
        });

        nbt.put("fastNodes", fastNodes);

        return nbt;
    }

    public static StartEndPathGenerator read(CompoundNBT readTag) {
        Long2ObjectArrayMap<List<Node>> fastNodes = new Long2ObjectArrayMap<>();
        ArrayList<Node> allNodes = new ArrayList<>();

        ListNBT readFastNodes = readTag.getList("fastNodes", 10);
        int distanceBetweenNodes = readTag.getInt("distanceBetweenNodes");
        int seed = readTag.getInt("seed");

        BlockPos startPos = getBlockPos(readTag.getIntArray("startPos"));
        BlockPos endPos = getBlockPos(readTag.getIntArray("endPos"));

        for (INBT rawNbt : readFastNodes) {
            CompoundNBT readFastNode = (CompoundNBT) rawNbt;

            long chunkPos = readFastNode.getLong("chunkPos");

            ListNBT readNodes = readTag.getList("nodes", 10);

            List<Node> nodes = new ArrayList<>();

            for (INBT rawNodeNbt : readNodes) {
                CompoundNBT readNode = (CompoundNBT) rawNodeNbt;

                BlockPos.Mutable pos = new BlockPos.Mutable().set(getBlockPos(readNode.getIntArray("pos")));

                int index = readNode.getInt("idx");
                int generated = readNode.getInt("generated");
                float angleOffset = readNode.getFloat("angleOffset");

                Node nbtNode = new Node(pos, index, generated, angleOffset);
                nodes.add(nbtNode);
                allNodes.add(nbtNode);
            }
            fastNodes.put(chunkPos, nodes);
        }

        return new StartEndPathGenerator(allNodes, fastNodes, WorldStructureAwarePathGenerator.createNoise(seed), startPos, endPos, distanceBetweenNodes);
    }

    public int[] writeBlockPos(BlockPos pos) {
        return new int[]{pos.getX(), pos.getY(), pos.getZ()};
    }

    public static BlockPos getBlockPos(int[] posArray) {
        return new BlockPos(posArray[0], posArray[1], posArray[2]);
    }

    // Angle -1.5 = West(Negative X)
    // Angle 0 = South(Positive Z)
    // Angle 1.5 = East(Positive X)
    // Angle 3 = North(Negative Z)

    Vector3i getAngleOffset(float angle) {
        return getAngleOffset(angle, this.distanceBetweenNodes);
    }


    public Vector3i getAngleOffset(float angle, int length) {
        Vector2f dAngle = get2DAngle(angle, length);
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


    public FastNoise getNoise() {
        return noise;
    }

    public int getDistanceBetweenNodes() {
        return distanceBetweenNodes;
    }

    static class Node {

        private final int idx;
        private final BlockPos.Mutable pos;
        private int heightAtLocation = 0;
        private int generatedForNode;
        private float angleOffset;
        private final List<BlockPos> failedPositions = new ArrayList<>();

        private Node(BlockPos.Mutable pos, int idx, int generatedForNode, float angleOffset) {
            this(pos, idx);
            this.generatedForNode = generatedForNode;
            this.angleOffset = angleOffset;
        }

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
                heightAtLocation = generator.getBaseHeight(pos.getX(), pos.getZ(), Heightmap.Type.WORLD_SURFACE_WG);
            }

            return heightAtLocation;
        }

        public int getGeneratedForNode() {
            return generatedForNode;
        }

        public void setGeneratedForNode(int generatedForNode) {
            this.generatedForNode = generatedForNode;
        }

        public float getAngleOffset() {
            return angleOffset;
        }

        public void setAngleOffset(float angleOffset) {
            this.angleOffset = angleOffset;
        }

        public List<BlockPos> getFailedPositions() {
            return failedPositions;
        }
    }
}
