package globe.world.block.entity;

import globe.world.GlobeWorldBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class GlobeBlockEntity extends BlockEntity {
    public GlobeBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
        super(GlobeWorldBlocks.GLOBE_BLOCK_ENTITY, worldPosition, blockState);
    }
}
