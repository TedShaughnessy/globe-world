package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import globe.world.topology.TopologicalRaycasts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityLookAtMeMixin {
    @WrapOperation(
            method = "isLookingAtMe",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getX()D",
                    ordinal = 0
            )
    )
    private double useLookedAtAliasX(
            LivingEntity lookedAt,
            Operation<Double> original,
            LivingEntity viewer,
            double coneSize,
            boolean adjustForDistance,
            boolean seeThroughTransparentBlocks,
            double[] gazeHeights) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition(viewer, lookedAt);
        return alias.x;
    }

    @WrapOperation(
            method = "isLookingAtMe",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D",
                    ordinal = 0
            )
    )
    private double useLookedAtAliasZ(
            LivingEntity lookedAt,
            Operation<Double> original,
            LivingEntity viewer,
            double coneSize,
            boolean adjustForDistance,
            boolean seeThroughTransparentBlocks,
            double[] gazeHeights) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition(viewer, lookedAt);
        return alias.z;
    }

    @WrapOperation(
            method = "isLookingAtMe",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hasLineOfSight(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;D)Z"
            )
    )
    private boolean useAliasLineOfSightForGaze(
            LivingEntity viewer,
            Entity lookedAt,
            ClipContext.Block blockClipType,
            ClipContext.Fluid fluidClipType,
            double targetY,
            Operation<Boolean> original,
            LivingEntity target,
            double coneSize,
            boolean adjustForDistance,
            boolean seeThroughTransparentBlocks,
            double[] gazeHeights) {
        if (original.call(viewer, lookedAt, blockClipType, fluidClipType, targetY)) {
            return true;
        }
        return ActorLocalTargets.canAlias(viewer, lookedAt)
                && TopologicalRaycasts.topologicalLineOfSight(viewer, lookedAt, blockClipType, fluidClipType, targetY);
    }
}
