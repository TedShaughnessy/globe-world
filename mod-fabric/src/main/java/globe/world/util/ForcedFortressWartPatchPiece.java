package globe.world.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public final class ForcedFortressWartPatchPiece extends StructurePiece {
    public ForcedFortressWartPatchPiece(BoundingBox boundingBox) {
        super(ForcedProgressionStructurePieces.FORTRESS_WART_PATCH, 0, boundingBox);
    }

    public ForcedFortressWartPatchPiece(CompoundTag tag) {
        super(ForcedProgressionStructurePieces.FORTRESS_WART_PATCH, tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
    }

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox box,
            ChunkPos chunkPos,
            BlockPos pivot) {
        BoundingBox patch = getBoundingBox();
        int soulSandY = patch.minY();
        int wartY = soulSandY + 1;
        for (int x = patch.minX(); x <= patch.maxX(); x++) {
            for (int z = patch.minZ(); z <= patch.maxZ(); z++) {
                BlockPos soulSandPos = new BlockPos(x, soulSandY, z);
                if (box.isInside(soulSandPos)) {
                    level.setBlock(soulSandPos, Blocks.SOUL_SAND.defaultBlockState(), 2);
                }

                BlockPos wartPos = new BlockPos(x, wartY, z);
                if (box.isInside(wartPos)) {
                    level.setBlock(wartPos, Blocks.NETHER_WART.defaultBlockState(), 2);
                }
            }
        }
    }
}
