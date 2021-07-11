package corgitaco.modid.structure;

import javax.annotation.Nullable;

public interface StructureNameContext {

    @Nullable
    String getStructureName();

    void setStructureName(String structureName);
}
