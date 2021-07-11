package corgitaco.modid.mixin;

import corgitaco.modid.structure.StructureNameContext;
import net.minecraft.world.gen.feature.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;

import javax.annotation.Nullable;

@Mixin(StructureStart.class)
public class MixinStructureStart implements StructureNameContext {

    @Nullable
    private String structureName;


    @Override
    public String getStructureName() {
        return this.structureName;
    }

    @Override
    public void setStructureName(String structureName) {
        this.structureName = structureName;
    }
}
