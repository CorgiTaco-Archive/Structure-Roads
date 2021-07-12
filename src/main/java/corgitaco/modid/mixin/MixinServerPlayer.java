package corgitaco.modid.mixin;

import corgitaco.modid.structure.AdditionalStructureContext;
import corgitaco.modid.structure.StructureNameContext;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.feature.structure.StructureStart;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayer {

    boolean sentMsg;

    @Shadow
    public abstract void displayClientMessage(ITextComponent p_146105_1_, boolean p_146105_2_);


    @Shadow
    public abstract ServerWorld getLevel();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void checkIfIntersectingVillage(CallbackInfo ci) {
        if (this.getLevel().getLevelData().getGameTime() % 20 == 0) {
            ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity) (Object) this;
            BlockPos currentPos = serverPlayerEntity.blockPosition();
            Optional<? extends StructureStart<?>> village = ((ServerWorld) serverPlayerEntity.level).startsForFeature(SectionPos.of(currentPos), Structure.VILLAGE).findFirst();
            if (village.isPresent()) {
                StructureStart<?> startForFeature = village.get();
                if (startForFeature.isValid()) {
                    AdditionalStructureContext structureName = ((StructureNameContext) startForFeature).getStructureName();

                    if (startForFeature.getBoundingBox().isInside(currentPos)) {
                        if (structureName != null && !sentMsg) {
                            this.displayClientMessage(new TranslationTextComponent("Now entering: %s", structureName.getName()), true);
                            sentMsg = true;
                        } else {
                            this.displayClientMessage(new TranslationTextComponent("Currently in: %s", structureName.getName()), true);
                        }
                    } else {
                        sentMsg = false;
                    }
                }
            }
        }
    }
}