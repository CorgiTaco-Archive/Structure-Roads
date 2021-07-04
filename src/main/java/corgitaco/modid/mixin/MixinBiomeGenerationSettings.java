package corgitaco.modid.mixin;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.world.biome.BiomeGenerationSettings;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.carver.ConfiguredCarver;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.StructureFeature;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.surfacebuilders.ConfiguredSurfaceBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Mixin(BiomeGenerationSettings.class)
public class MixinBiomeGenerationSettings {


    @Shadow
    @Final
    private List<Supplier<StructureFeature<?, ?>>> structureStarts;

    private final ObjectOpenHashSet<Structure<?>> structures = new ObjectOpenHashSet<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cacheStructureStarts(Supplier<ConfiguredSurfaceBuilder<?>> p_i241935_1_, Map<GenerationStage.Carving, List<Supplier<ConfiguredCarver<?>>>> p_i241935_2_, List<List<Supplier<ConfiguredFeature<?, ?>>>> p_i241935_3_, List<Supplier<StructureFeature<?, ?>>> p_i241935_4_, CallbackInfo ci) {
        structures.addAll(this.structureStarts.stream().map((structureFeature) -> {
            return (structureFeature.get()).feature;
        }).collect(Collectors.toSet()));
    }

    @Inject(method = "isValidStart", at = @At("HEAD"), cancellable = true)
    public void isValidStart(Structure<?> structure, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(this.structures.contains(structure));
    }
}
