package corgitaco.modid.mixin;

import corgitaco.modid.Main;
import corgitaco.modid.mixin.access.ChunkManagerAccess;
import corgitaco.modid.path.WarpedStartEndGenerator;
import corgitaco.modid.path.PathGeneratorsWorldContext;
import corgitaco.modid.structure.AdditionalStructureContext;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.listener.IChunkStatusListener;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.spawner.ISpecialSpawner;
import net.minecraft.world.storage.IServerWorldInfo;
import net.minecraft.world.storage.SaveFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@Mixin(ServerWorld.class)
public abstract class MixinServerLevel implements PathGeneratorsWorldContext {
    @Shadow
    public abstract ServerChunkProvider getChunkSource();

    private final Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>> structurePositionsToName = new Long2ReferenceOpenHashMap<>();
    private final Long2ReferenceOpenHashMap<ArrayList<WarpedStartEndGenerator>> regionPathGenerators = new Long2ReferenceOpenHashMap<>();
    private Path worldStructuresCache;
    private Path worldGeneratorsCache;


    @Inject(method = "<init>", at = @At("RETURN"))
    private void attachStructurePathsContext(MinecraftServer server, Executor executor, SaveFormat.LevelSave save, IServerWorldInfo worldInfo, RegistryKey<World> key, DimensionType dimensionType, IChunkStatusListener listener, ChunkGenerator generator, boolean p_i241885_9_, long p_i241885_10_, List<ISpecialSpawner> p_i241885_12_, boolean p_i241885_13_, CallbackInfo ci) {
        this.worldStructuresCache = ((ChunkManagerAccess) this.getChunkSource().chunkMap).getStorageFolder().toPath().resolve(Main.MOD_ID).resolve("structures");
        if (!worldStructuresCache.toFile().exists()) {
            try {
                Files.createDirectories(this.worldStructuresCache);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.worldGeneratorsCache = ((ChunkManagerAccess) this.getChunkSource().chunkMap).getStorageFolder().toPath().resolve(Main.MOD_ID).resolve("generators");
        if (!worldGeneratorsCache.toFile().exists()) {
            try {
                Files.createDirectories(this.worldGeneratorsCache);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>> getRegionStructurePositionsToContext() {
        return structurePositionsToName;
    }

    @Override
    public Long2ReferenceOpenHashMap<ArrayList<WarpedStartEndGenerator>> getRegionPathGenerators() {
        return regionPathGenerators;
    }

    @Override
    public Path getWorldStructuresStorage() {
        return worldStructuresCache;
    }

    @Override
    public Path getWorldGeneratorStorage() {
        return worldGeneratorsCache;
    }
}
