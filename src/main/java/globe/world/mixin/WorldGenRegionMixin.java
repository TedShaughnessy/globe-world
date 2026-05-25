package globe.world.mixin;

import globe.world.util.CoordUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(WorldGenRegion.class)
public class WorldGenRegionMixin {
    @ModifyVariable(method = "getBlockState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenGetBlockStatePos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(pos);
    }

    @ModifyVariable(method = "getFluidState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenGetFluidStatePos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(pos);
    }

    @ModifyVariable(method = "getBlockEntity", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenGetBlockEntityPos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(pos);
    }

    @ModifyVariable(method = "setBlock", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenSetBlockPos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(pos);
    }
}
