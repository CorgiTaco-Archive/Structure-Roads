package corgitaco.modid.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import corgitaco.modid.debug.StructureBoxDebug;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {


    private final StructureBoxDebug debugBox = new StructureBoxDebug();

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/debug/DebugRenderer$IDebugRenderer;render(Lcom/mojang/blaze3d/matrix/MatrixStack;Lnet/minecraft/client/renderer/IRenderTypeBuffer;DDD)V"), cancellable = true)
    private void yeet(MatrixStack stack, IRenderTypeBuffer.Impl impl, double x, double y, double z, CallbackInfo ci) {
        ci.cancel();
        this.debugBox.render(stack, impl, x, y, z);
    }
}