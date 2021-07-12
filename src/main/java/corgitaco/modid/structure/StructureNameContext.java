package corgitaco.modid.structure;

import javax.annotation.Nullable;

public interface StructureNameContext {

    @Nullable
    AdditionalStructureContext getStructureName();

    void setStructureName(AdditionalStructureContext structureName);
}
