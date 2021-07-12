package corgitaco.modid.mixin;

import corgitaco.modid.structure.AdditionalStructureContext;
import corgitaco.modid.structure.StructureNameContext;
import net.minecraft.world.gen.feature.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;

import javax.annotation.Nullable;

@Mixin(StructureStart.class)
public class MixinStructureStart implements StructureNameContext {

    @Nullable
    private AdditionalStructureContext structureName;


    @Override
    public AdditionalStructureContext getStructureName() {
        return this.structureName;
    }

    @Override
    public void setStructureName(AdditionalStructureContext structureName) {
        this.structureName = structureName;
    }
}
