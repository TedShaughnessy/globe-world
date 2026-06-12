package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologyContexts;
import globe.world.util.CoordUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.trialspawner.PlayerDetector;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerDetector.class)
public interface PlayerDetectorMixin {
    @WrapOperation(
        method = {"lambda$static$1", "lambda$static$4"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"
        )
    )
    private static boolean useWrappedDistanceForDetectedPlayer(
            BlockPos playerPos,
            Vec3i spawnerPos,
            double distance,
            Operation<Boolean> original,
            BlockPos detectorPos,
            double requiredPlayerRange,
            Player player) {
        double distanceSqr = CoordUtil.wrappedDistanceSqr(
                player.level(),
                playerPos.getX(),
                playerPos.getY(),
                playerPos.getZ(),
                spawnerPos.getX(),
                spawnerPos.getY(),
                spawnerPos.getZ());
        return distanceSqr < distance * distance;
    }

    @WrapOperation(
        method = {"lambda$static$2", "lambda$static$5"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/trialspawner/PlayerDetector;inLineOfSight(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Z"
        )
    )
    private static boolean useVisibleDetectorForLineOfSight(
            Level level,
            Vec3 origin,
            Vec3 dest,
            Operation<Boolean> original,
            boolean requireLineOfSight,
            ServerLevel serverLevel,
            BlockPos detectorPos,
            Player player) {
        Vec3 visibleOrigin = TopologyContexts.forLevel(serverLevel).virtualBlockForViewer(origin, dest);
        return original.call(level, visibleOrigin, dest);
    }
}
