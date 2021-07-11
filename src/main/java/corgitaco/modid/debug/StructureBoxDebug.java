package corgitaco.modid.debug;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.gen.feature.structure.Structure;

public class StructureBoxDebug implements DebugRenderer.IDebugRenderer {



    @Override
    public void render(MatrixStack stack, IRenderTypeBuffer renderTypeBuffer, double x, double y, double z) {
        int range = 10;


        ClientPlayerEntity player = Minecraft.getInstance().player;
        IntegratedServer singleplayerServer = Minecraft.getInstance().getSingleplayerServer();

        if (singleplayerServer == null) {
            return;
        }

        IVertexBuilder ivertexbuilder = renderTypeBuffer.getBuffer(RenderType.lines());

        for (int chunkX = player.xChunk - range; chunkX < player.xChunk + range; chunkX++) {
            for (int chunkZ = player.zChunk - range; chunkZ < player.zChunk + range; chunkZ++) {
                singleplayerServer.getLevel(Minecraft.getInstance().level.dimension()).startsForFeature(SectionPos.of(player.blockPosition()), Structure.VILLAGE).forEach(structureStart -> {
                    MutableBoundingBox mutableboundingbox1 = structureStart.getBoundingBox();
                    WorldRenderer.renderLineBox(stack, ivertexbuilder, (double)mutableboundingbox1.x0 - x, (double)mutableboundingbox1.y0 - y, (double)mutableboundingbox1.z0 - z, (double)(mutableboundingbox1.x1 + 1) - x, (double)(mutableboundingbox1.y1 + 1) - y, (double)(mutableboundingbox1.z1 + 1) - z, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F);
                });
            }
        }
    }
}
