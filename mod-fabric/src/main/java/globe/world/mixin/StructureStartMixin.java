package globe.world.mixin;

import globe.world.util.DimensionTiling;
import globe.world.util.StructurePlacementShifts;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureStart.class)
public class StructureStartMixin {
    @Inject(method = "placeInChunk", at = @At("HEAD"), cancellable = true)
    private void placeWithStoredToroidalShift(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox chunkBB,
            ChunkPos chunkPos,
            CallbackInfo ci) {
        if (!DimensionTiling.forLevel(level.getLevel()).enabled() || StructurePlacementShifts.isPlacingShifted()) {
            return;
        }

        StructurePlacementShifts.Shift shift = StructurePlacementShifts.consume((StructureStart) (Object) this);
        if (shift == null || shift.isZero()) {
            return;
        }

        // The shift is a transient worldgen view recovered from a reference key.
        // The structure start itself remains owned by its canonical chunk.
        BoundingBox shiftedChunkBB = chunkBB.moved(shift.blockX(), 0, shift.blockZ());
        ChunkPos shiftedChunkPos = new ChunkPos(chunkPos.x() + shift.chunkX(), chunkPos.z() + shift.chunkZ());

        StructurePlacementShifts.setPlacingShifted(true);
        try {
            ((StructureStart) (Object) this).placeInChunk(level, structureManager, generator, random, shiftedChunkBB, shiftedChunkPos);
        } finally {
            StructurePlacementShifts.setPlacingShifted(false);
        }

        ci.cancel();
    }
}
