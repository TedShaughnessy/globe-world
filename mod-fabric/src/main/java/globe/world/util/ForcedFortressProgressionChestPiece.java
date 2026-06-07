package globe.world.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public final class ForcedFortressProgressionChestPiece extends StructurePiece {
    private static final int WIDTH = 3;
    private static final int HEIGHT = 3;
    private static final int DEPTH = 3;
    private static final int CHEST_SLOT = 13;

    public ForcedFortressProgressionChestPiece(int x, int y, int z, Direction direction) {
        super(
                ForcedProgressionStructurePieces.FORTRESS_PROGRESSION_CHEST,
                0,
                BoundingBox.orientBox(x, y, z, 0, 0, 0, WIDTH, HEIGHT, DEPTH, direction)
        );
        setOrientation(direction);
    }

    public ForcedFortressProgressionChestPiece(CompoundTag tag) {
        super(ForcedProgressionStructurePieces.FORTRESS_PROGRESSION_CHEST, tag);
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
        BlockPos chestPos = getWorldPos(1, 1, 1);
        if (!box.isInside(chestPos)) {
            return;
        }

        Direction facing = getOrientation() != null ? getOrientation() : Direction.NORTH;
        BlockState chestState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
        level.setBlock(chestPos, chestState, 2);
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setItem(CHEST_SLOT, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            chest.setChanged();
        }
    }
}
