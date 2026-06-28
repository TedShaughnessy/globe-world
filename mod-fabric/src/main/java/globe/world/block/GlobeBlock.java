package globe.world.block;

import com.mojang.serialization.MapCodec;
import globe.world.GlobeWorldBlocks;
import globe.world.atlas.GlobeAtlasPowerState;
import globe.world.atlas.GlobeAtlasPowers;
import globe.world.atlas.GlobeAtlasSurvey;
import globe.world.block.entity.GlobeBlockEntity;
import globe.world.util.DimensionTiling;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class GlobeBlock extends BaseEntityBlock {
    public static final MapCodec<GlobeBlock> CODEC = simpleCodec(GlobeBlock::new);
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape FLOOR_SHAPE = Shapes.or(
            Block.box(3.0D, 0.0D, 3.0D, 13.0D, 3.0D, 13.0D),
            Block.box(5.0D, 3.0D, 5.0D, 11.0D, 7.0D, 11.0D));
    private static final VoxelShape CEILING_SHAPE = Shapes.or(
            Block.box(3.0D, 13.0D, 3.0D, 13.0D, 16.0D, 13.0D),
            Block.box(5.0D, 9.0D, 5.0D, 11.0D, 13.0D, 11.0D));
    private static final Map<Direction, VoxelShape> WALL_SHAPES = Map.of(
            Direction.NORTH,
            Shapes.or(
                    Block.box(3.0D, 3.0D, 13.0D, 13.0D, 13.0D, 16.0D),
                    Block.box(5.0D, 5.0D, 9.0D, 11.0D, 11.0D, 13.0D)),
            Direction.SOUTH,
            Shapes.or(
                    Block.box(3.0D, 3.0D, 0.0D, 13.0D, 13.0D, 3.0D),
                    Block.box(5.0D, 5.0D, 3.0D, 11.0D, 11.0D, 7.0D)),
            Direction.EAST,
            Shapes.or(
                    Block.box(0.0D, 3.0D, 3.0D, 3.0D, 13.0D, 13.0D),
                    Block.box(3.0D, 5.0D, 5.0D, 7.0D, 11.0D, 11.0D)),
            Direction.WEST,
            Shapes.or(
                    Block.box(13.0D, 3.0D, 3.0D, 16.0D, 13.0D, 13.0D),
                    Block.box(9.0D, 5.0D, 5.0D, 13.0D, 11.0D, 11.0D)));

    private final Function<BlockState, VoxelShape> shapes;

    public GlobeBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(FACING, Direction.NORTH));
        this.shapes = this.getShapeForEachState(GlobeBlock::shapeForState);
    }

    @Override
    public MapCodec<GlobeBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
        return new GlobeBlockEntity(worldPosition, blockState);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level,
            final BlockState blockState,
            final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(
                type,
                GlobeWorldBlocks.GLOBE_BLOCK_ENTITY,
                (serverLevel, pos, state, globe) -> GlobeAtlasPowers.tickBlockEntity((ServerLevel)serverLevel, pos, globe));
    }

    @Override
    protected RenderShape getRenderShape(final BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        for (Direction direction : context.getNearestLookingDirections()) {
            BlockState state;
            if (direction.getAxis() == Direction.Axis.Y) {
                state = this.defaultBlockState()
                        .setValue(FACE, direction == Direction.UP ? AttachFace.CEILING : AttachFace.FLOOR)
                        .setValue(FACING, context.getHorizontalDirection());
            } else {
                state = this.defaultBlockState()
                        .setValue(FACE, AttachFace.WALL)
                        .setValue(FACING, direction.getOpposite());
            }

            if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                return state;
            }
        }

        return null;
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
            if (player.isShiftKeyDown()) {
                boolean projectionEnabled = false;
                if (!GlobeAtlasSurvey.surveyMode(DimensionTiling.forLevel(level))) {
                    projectionEnabled = globe.toggleProjection();
                } else {
                    globe.setProjectionEnabled(false);
                }
                level.playSound(
                        null,
                        pos,
                        SoundEvents.COMPARATOR_CLICK,
                        SoundSource.BLOCKS,
                        0.3F,
                        projectionEnabled ? 1.1F : 0.75F);
            } else if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                GlobeAtlasPowers.openScreen(serverPlayer, pos);
            }
            return InteractionResult.SUCCESS_SERVER;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(
            final BlockState state,
            final ServerLevel level,
            final BlockPos pos,
            final boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        GlobeAtlasPowerState.getIfOverworld(level).ifPresent(powerState -> powerState.remove(pos));
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return this.shapes.apply(state);
    }

    @Override
    protected VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return this.shapes.apply(state);
    }

    @Override
    protected boolean canSurvive(final BlockState state, final LevelReader level, final BlockPos pos) {
        return canAttach(level, pos, getConnectedDirection(state).getOpposite());
    }

    @Override
    protected BlockState updateShape(
            final BlockState state,
            final LevelReader level,
            final ScheduledTickAccess ticks,
            final BlockPos pos,
            final Direction directionToNeighbour,
            final BlockPos neighbourPos,
            final BlockState neighbourState,
            final RandomSource random) {
        return getConnectedDirection(state).getOpposite() == directionToNeighbour && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected BlockState rotate(final BlockState state, final Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(final BlockState state, final Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    public static Direction getConnectedDirection(final BlockState state) {
        return switch (state.getValue(FACE)) {
            case CEILING -> Direction.DOWN;
            case FLOOR -> Direction.UP;
            case WALL -> state.getValue(FACING);
        };
    }

    private static boolean canAttach(final LevelReader level, final BlockPos pos, final Direction direction) {
        BlockPos relative = pos.relative(direction);
        return level.getBlockState(relative).isFaceSturdy(level, relative, direction.getOpposite());
    }

    private static VoxelShape shapeForState(final BlockState state) {
        return switch (state.getValue(FACE)) {
            case CEILING -> CEILING_SHAPE;
            case FLOOR -> FLOOR_SHAPE;
            case WALL -> WALL_SHAPES.get(state.getValue(FACING));
        };
    }
}
