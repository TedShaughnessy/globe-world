package globe.world.block;

import com.mojang.serialization.MapCodec;
import globe.world.block.entity.GlobeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GlobeBlock extends BaseEntityBlock {
    public static final MapCodec<GlobeBlock> CODEC = simpleCodec(GlobeBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3.0D, 0.0D, 3.0D, 13.0D, 3.0D, 13.0D),
            Block.box(5.0D, 3.0D, 5.0D, 11.0D, 7.0D, 11.0D));

    public GlobeBlock(final BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<GlobeBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
        return new GlobeBlockEntity(worldPosition, blockState);
    }

    @Override
    protected RenderShape getRenderShape(final BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected InteractionResult useWithoutItem(
            final BlockState state,
            final Level level,
            final BlockPos pos,
            final Player player,
            final BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof GlobeBlockEntity globe) {
            globe.cycleWrapAxis();
            level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, 0.9F);
            return InteractionResult.SUCCESS_SERVER;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return SHAPE;
    }
}
