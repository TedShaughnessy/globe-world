package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import globe.world.util.StructurePlacementShifts;
import globe.world.util.WorldGenSpillover;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"), cancellable = true)
    private void skipAliasBiomeDecoration(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager,
            CallbackInfo ci) {
        StructurePlacementShifts.clear();
        if (!isCanonical(chunk.getPos())) {
            ci.cancel();
            return;
        }

        WorldGenSpillover.applyToChunk(level.getLevel(), chunk);
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void applyQueuedSpilloverAfterBiomeDecoration(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager,
            CallbackInfo ci) {
        StructurePlacementShifts.clear();
        if (isCanonical(chunk.getPos())) {
            WorldGenSpillover.applyToChunk(level.getLevel(), chunk);
        }
    }

    @Inject(method = "createReferences", at = @At("HEAD"), cancellable = true)
    private void createToroidalStructureReferences(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkAccess centerChunk,
            CallbackInfo ci) {
        if (!isCanonical(centerChunk.getPos())) {
            centerChunk.setAllReferences(Collections.emptyMap());
            ci.cancel();
            return;
        }

        addToroidalStructureReferences(level, structureManager, centerChunk);
        ci.cancel();
    }

    @WrapOperation(
            method = "applyBiomeDecoration",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/StructureManager;startsForStructure(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/levelgen/structure/Structure;)Ljava/util/List;"
            )
    )
    private List<StructureStart> startsForToroidalStructure(
            StructureManager structureManager,
            SectionPos sectionPos,
            Structure structure,
            Operation<List<StructureStart>> original,
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager methodStructureManager) {
        if (!isCanonical(chunk.getPos())) {
            return original.call(structureManager, sectionPos, structure);
        }

        LongSet references = level.getChunk(
                sectionPos.x(),
                sectionPos.z(),
                ChunkStatus.STRUCTURE_REFERENCES
        ).getReferencesForStructure(structure);
        List<StructureStart> starts = new ArrayList<>();
        LongIterator iterator = references.iterator();
        while (iterator.hasNext()) {
            long referenceKey = iterator.nextLong();
            ChunkPos virtualSource = ChunkPos.unpack(referenceKey);
            ChunkAccess sourceChunk = level.getChunk(virtualSource.x(), virtualSource.z(), ChunkStatus.STRUCTURE_STARTS);
            StructureStart start = structureManager.getStartForStructure(SectionPos.bottomOf(sourceChunk), structure, sourceChunk);
            if (start == null || !start.isValid()) {
                continue;
            }

            starts.add(start);
            StructurePlacementShifts.enqueue(
                    start,
                    virtualSource.x() - start.getChunkPos().x(),
                    virtualSource.z() - start.getChunkPos().z()
            );
        }
        return starts;
    }

    private static boolean isCanonical(ChunkPos pos) {
        return CoordUtil.wrapChunk(pos.x()) == pos.x() && CoordUtil.wrapChunk(pos.z()) == pos.z();
    }

    private static void addToroidalStructureReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess centerChunk) {
        ChunkPos targetPos = centerChunk.getPos();
        int targetX = targetPos.x();
        int targetZ = targetPos.z();
        int targetBlockX = targetPos.getMinBlockX();
        int targetBlockZ = targetPos.getMinBlockZ();
        SectionPos sectionPos = SectionPos.bottomOf(centerChunk);

        for (int sourceX = targetX - ChunkStatus.MAX_STRUCTURE_DISTANCE; sourceX <= targetX + ChunkStatus.MAX_STRUCTURE_DISTANCE; sourceX++) {
            for (int sourceZ = targetZ - ChunkStatus.MAX_STRUCTURE_DISTANCE; sourceZ <= targetZ + ChunkStatus.MAX_STRUCTURE_DISTANCE; sourceZ++) {
                ChunkAccess sourceChunk = level.getChunk(sourceX, sourceZ, ChunkStatus.STRUCTURE_STARTS);
                int shiftX = (sourceX - sourceChunk.getPos().x()) * 16;
                int shiftZ = (sourceZ - sourceChunk.getPos().z()) * 16;

                for (StructureStart start : sourceChunk.getAllStarts().values()) {
                    BoundingBox shiftedBounds = start.getBoundingBox().moved(shiftX, 0, shiftZ);
                    if (start.isValid() && shiftedBounds.intersects(targetBlockX, targetBlockZ, targetBlockX + 15, targetBlockZ + 15)) {
                        structureManager.addReferenceForStructure(
                                sectionPos,
                                start.getStructure(),
                                ChunkPos.pack(sourceX, sourceZ),
                                centerChunk
                        );
                    }
                }
            }
        }
    }
}
