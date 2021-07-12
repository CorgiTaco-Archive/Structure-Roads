package corgitaco.modid.mixin;

import com.mojang.datafixers.util.Either;
import corgitaco.modid.Main;
import corgitaco.modid.mixin.access.SingleJigsawPieceAccess;
import corgitaco.modid.structure.StructureNameContext;
import net.minecraft.block.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.jigsaw.JigsawPiece;
import net.minecraft.world.gen.feature.jigsaw.SingleJigsawPiece;
import net.minecraft.world.gen.feature.structure.AbstractVillagePiece;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.feature.structure.StructurePiece;
import net.minecraft.world.gen.feature.structure.StructureStart;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ChunkHolder.class)
public class MixinChunkHolder {

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void runChunkUpdates(Chunk chunk, CallbackInfo ci) {
        upgradeStructure(chunk);

    }

    private void upgradeStructure(Chunk chunk) {
        ServerWorld serverLevel = (ServerWorld) chunk.getLevel();
        StructureStart<?> villageStart = chunk.getStartForFeature(Structure.VILLAGE);
        if (villageStart != null && villageStart.isValid() && !((StructureNameContext) villageStart).getStructureName().wasChanged()) {
            if (serverLevel.getGameTime() % 500 == 0) {
                ((StructureNameContext) villageStart).getStructureName().markChanged();
                for (StructurePiece piece : villageStart.getPieces()) {
                    if (piece instanceof AbstractVillagePiece) {
                        JigsawPiece element = ((AbstractVillagePiece) piece).getElement();

                        if (element instanceof SingleJigsawPiece) {
                            Either<ResourceLocation, Template> template = ((SingleJigsawPieceAccess) element).getTemplate();
                            Optional<ResourceLocation> location = template.left();
                            if (location.isPresent()) {
                                ResourceLocation targetToReplace = new ResourceLocation("village/plains/houses/plains_small_house_3");
                                ResourceLocation current = location.get();
                                if (current.equals(targetToReplace)) {
                                    ResourceLocation newHouse = new ResourceLocation(Main.MOD_ID, "village/plains/houses/upgraded_plains_small_house_3");
                                    Template newTemplate = serverLevel.getStructureManager().getOrCreate(newHouse);
                                    Either<ResourceLocation, Template> eitherTemplate = Either.left(newHouse);
                                    ((SingleJigsawPieceAccess) element).setTemplate(eitherTemplate);

                                    MutableBoundingBox boundingBox = piece.getBoundingBox();

                                    BlockPos.Mutable mutable = new BlockPos.Mutable();
                                    for (int xClear = boundingBox.x0; xClear <= boundingBox.x1; xClear++) {
                                        for (int zClear = boundingBox.z0; zClear <= boundingBox.z1; zClear++) {
                                            for (int yClear = boundingBox.y0; yClear <= boundingBox.y1; yClear++) {
                                                serverLevel.setBlock(mutable.set(xClear, yClear, zClear), Blocks.AIR.defaultBlockState(), 2);
                                            }
                                        }
                                    }
                                    MutableBoundingBox newBB = new MutableBoundingBox(boundingBox.x0, boundingBox.y0, boundingBox.z0, boundingBox.x1, serverLevel.getHeight(), boundingBox.z1);
                                    newTemplate.placeInWorld(serverLevel, new BlockPos(boundingBox.x0, boundingBox.y0, boundingBox.z0), ((SingleJigsawPieceAccess) element).invokeGetSettings(piece.getRotation(), newBB, true), serverLevel.getRandom());
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}