package corgitaco.modid.structure;

import corgitaco.modid.river.perlin.WarpedStartEndGenerator;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;

import java.nio.file.Path;
import java.util.ArrayList;

public interface PathGeneratorsWorldContext {

    Long2ReferenceOpenHashMap<Long2ObjectArrayMap<String>> getRegionStructurePositionsToName();

    Long2ReferenceOpenHashMap<ArrayList<WarpedStartEndGenerator>> getRegionPathGenerators();

    Path getWorldStructuresStorage();

    Path getWorldGeneratorStorage();
}
