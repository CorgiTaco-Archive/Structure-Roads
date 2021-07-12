package corgitaco.modid.path;

import corgitaco.modid.structure.AdditionalStructureContext;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;

import java.nio.file.Path;
import java.util.ArrayList;

public interface PathGeneratorsWorldContext {

    Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>> getRegionStructurePositionsToContext();

    Long2ReferenceOpenHashMap<ArrayList<WarpedStartEndGenerator>> getRegionPathGenerators();

    Path getWorldStructuresStorage();

    Path getWorldGeneratorStorage();
}
