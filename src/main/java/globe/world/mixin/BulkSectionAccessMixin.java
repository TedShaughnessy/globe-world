package globe.world.mixin;

import globe.world.util.CoordUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BulkSectionAccess.class)
public class BulkSectionAccessMixin {
    @ModifyVariable(method = "getSection", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeBulkSectionPos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(pos);
    }
}
