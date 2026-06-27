package globe.world.block;

import com.mojang.serialization.MapCodec;
import globe.world.block.entity.GlobeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GlobeBlock extends BaseEntityBlock {
    public static final MapCodec<GlobeBlock> CODEC = simpleCodec(GlobeBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(5.0D, 0.0D, 5.0D, 11.0D, 2.0D, 11.0D),
            Block.box(3.0D, 2.0D, 3.0D, 13.0D, 4.0D, 13.0D),
            Block.box(2.0D, 4.0D, 2.0D, 14.0D, 12.0D, 14.0D),
            Block.box(3.0D, 12.0D, 3.0D, 13.0D, 14.0D, 13.0D),
            Block.box(5.0D, 14.0D, 5.0D, 11.0D, 16.0D, 11.0D));

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
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return SHAPE;
    }
}
