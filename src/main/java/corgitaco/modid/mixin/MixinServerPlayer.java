package corgitaco.modid.mixin;

import corgitaco.modid.structure.StructureNameContext;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.feature.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayer {

    boolean sentMsg;

    @Shadow
    public abstract void displayClientMessage(ITextComponent p_146105_1_, boolean p_146105_2_);


    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void checkPos(CallbackInfo ci) {
        ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity) (Object) this;
        BlockPos currentPos = serverPlayerEntity.blockPosition();
        int playerChunkX = SectionPos.blockToSectionCoord(currentPos.getX());
        int playerChunkZ = SectionPos.blockToSectionCoord(currentPos.getZ());

        int range = 5;
        for (int chunkX = playerChunkX - range; chunkX < playerChunkX + range; chunkX++) {
            for (int chunkZ = playerChunkZ - range; chunkZ < playerChunkZ + range; chunkZ++) {
                if (serverPlayerEntity.level.hasChunk(chunkX, chunkZ)) {
                    Chunk chunk = serverPlayerEntity.level.getChunk(chunkX, chunkZ);
                    StructureStart<?> startForFeature = chunk.getStartForFeature(Structure.VILLAGE);
                    if (startForFeature != null && startForFeature.isValid()) {
                        String structureName = ((StructureNameContext) startForFeature).getStructureName();

                        if (startForFeature.getBoundingBox().isInside(currentPos)) {
                            if (structureName != null && !sentMsg) {
                                this.displayClientMessage(new TranslationTextComponent("Now entering: %s", structureName), true);
                                sentMsg = true;
                            } else {
                                this.displayClientMessage(new TranslationTextComponent("Currently in: %s", structureName), true);
                            }
                        } else {
                            sentMsg = false;
                        }
                        break;
                    }
                }
            }
        }

    }
}
