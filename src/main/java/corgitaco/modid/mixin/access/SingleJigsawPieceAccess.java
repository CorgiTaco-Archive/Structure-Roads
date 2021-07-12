package corgitaco.modid.mixin.access;


import com.mojang.datafixers.util.Either;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.world.gen.feature.jigsaw.SingleJigsawPiece;
import net.minecraft.world.gen.feature.template.PlacementSettings;
import net.minecraft.world.gen.feature.template.Template;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SingleJigsawPiece.class)
public interface SingleJigsawPieceAccess {

    @Accessor
    Either<ResourceLocation, Template> getTemplate();

    @Accessor
    void setTemplate(Either<ResourceLocation, Template> newTemplate);

    @Invoker
    PlacementSettings invokeGetSettings(Rotation rotation, MutableBoundingBox bb, boolean bl);

}
