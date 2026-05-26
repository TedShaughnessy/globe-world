package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin {
    @WrapOperation(
            method = "getPortalDestination",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/border/WorldBorder;clampToBounds(DDD)Lnet/minecraft/core/BlockPos;"
            )
    )
    private BlockPos canonicalizeTargetApproximateExit(
            WorldBorder worldBorder,
            double x,
            double y,
            double z,
            Operation<BlockPos> original,
            ServerLevel currentLevel,
            Entity entity,
            BlockPos portalEntryPos) {
        BlockPos approximateExit = original.call(worldBorder, x, y, z);
        ServerLevel newLevel = currentLevel.getServer().getLevel(currentLevel.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER);
        return newLevel == null ? approximateExit : CoordUtil.wrapBlockPos(newLevel, approximateExit);
    }
}
