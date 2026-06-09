package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.config.GlobeConfig;
import globe.world.util.CoordUtil;
import globe.world.util.PortalDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin {
    @Inject(method = "entityInside", at = @At("HEAD"))
    private void logServerPortalContact(
            BlockState state,
            Level level,
            BlockPos pos,
            Entity entity,
            InsideBlockEffectApplier effectApplier,
            boolean isPrecise,
            CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel) {
            PortalDiagnostics.portalContact(serverLevel, entity, pos, state);
        }
    }

    @Inject(method = "getPortalDestination", at = @At("RETURN"))
    private void logPortalDestination(
            ServerLevel currentLevel,
            Entity entity,
            BlockPos portalEntryPos,
            CallbackInfoReturnable<TeleportTransition> cir) {
        PortalDiagnostics.portalDestination(currentLevel, entity, portalEntryPos, cir.getReturnValue());
    }

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
        ServerLevel newLevel = currentLevel.getServer().getLevel(Level.NETHER.equals(currentLevel.dimension()) ? Level.OVERWORLD : Level.NETHER);
        if (newLevel == null) {
            return original.call(worldBorder, x, y, z);
        }

        double teleportationScale = GlobeConfig.netherPortalTeleportationScale(currentLevel, newLevel);
        double sourceX = CoordUtil.wrapBlock(currentLevel, entity.getX());
        double sourceZ = CoordUtil.wrapBlock(currentLevel, entity.getZ());
        BlockPos approximateExit = original.call(worldBorder, sourceX * teleportationScale, y, sourceZ * teleportationScale);
        return CoordUtil.wrapBlockPos(newLevel, approximateExit);
    }
}
