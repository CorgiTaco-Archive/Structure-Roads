package corgitaco.modid.mixin;


import corgitaco.modid.structure.PathGeneratorsWorldContext;
import corgitaco.modid.structure.StructureNameContext;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.village.PointOfInterestManager;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.storage.ChunkSerializer;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.feature.structure.StructureStart;
import net.minecraft.world.gen.feature.template.TemplateManager;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

import static corgitaco.modid.river.perlin.WorldStructureAwareWarpedPathGenerator.*;

@Mixin(ChunkSerializer.class)
public class MixinChunkSerializer {


    @Inject(method = "read", at = @At("RETURN"))
    private static void attachStructureName(ServerWorld world, TemplateManager templateManager, PointOfInterestManager poiManager, ChunkPos pos, CompoundNBT nbt, CallbackInfoReturnable<ChunkPrimer> cir) {
        Map<Structure<?>, StructureStart<?>> allStarts = cir.getReturnValue().getAllStarts();
        if (allStarts.containsKey(Structure.VILLAGE)) {
            StructureStart<?> structureStart = allStarts.get(Structure.VILLAGE);
            if (structureStart.isValid()) {
                int x0 = structureStart.getBoundingBox().x0;
                int z0 = structureStart.getBoundingBox().z0;
                int x1 = structureStart.getBoundingBox().x1;
                int z1 = structureStart.getBoundingBox().z1;

                int minChunkX = SectionPos.blockToSectionCoord(x0);
                int minChunkZ = SectionPos.blockToSectionCoord(z0);

                int maxChunkX = SectionPos.blockToSectionCoord(x1);
                int maxChunkZ = SectionPos.blockToSectionCoord(z1);

                long currentChunk = pos.toLong();
                long currentRegion = regionLong(chunkToRegion(pos.x), chunkToRegion(pos.z));

                PathGeneratorsWorldContext pathGeneratorsWorldContext = (PathGeneratorsWorldContext) world;
                ChunkGenerator generator = world.getChunkSource().generator;
                StructureSeparationSettings config = generator.getSettings().getConfig(Structure.VILLAGE);
                Long2ReferenceOpenHashMap<Long2ObjectArrayMap<String>> regionStructurePositionsToName = pathGeneratorsWorldContext.getRegionStructurePositionsToName();

                for (int chunkX = minChunkX; chunkX < maxChunkX; chunkX++) {
                    for (int chunkZ = minChunkZ; chunkZ < maxChunkZ; chunkZ++) {
                        int regionX = chunkToRegion(chunkX);
                        int regionZ = chunkToRegion(chunkZ);
                        long activeRegion = regionLong(regionX, regionZ);

                        if (!regionStructurePositionsToName.containsKey(activeRegion)) {
                            addRegionStructuresToCache(world.getSeed(), pathGeneratorsWorldContext.getWorldStructuresStorage(), generator.getBiomeSource(), Structure.VILLAGE, config, config.spacing(), regionStructurePositionsToName, regionX, regionZ, activeRegion);
                        }
                    }
                }

                Long2ObjectArrayMap<String> structureToStructureName = regionStructurePositionsToName.get(currentRegion);
                if (structureToStructureName.containsKey(currentChunk)) {
                    ((StructureNameContext) structureStart).setStructureName(structureToStructureName.get(currentChunk));
                }
            }
        }
    }
}
