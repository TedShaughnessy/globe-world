package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Ghast.class)
public class GhastFacingMixin {
    @WrapOperation(
            method = "faceMovementDirection",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private static double useAliasDistanceForTargetFacing(
            LivingEntity target,
            Entity ghast,
            Operation<Double> original,
            @Local(argsOnly = true) Mob mob) {
        return ActorLocalTargets.distanceToSqr(mob, target);
    }

    @WrapOperation(
            method = "faceMovementDirection",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D")
    )
    private static double faceAliasTargetX(LivingEntity target, Operation<Double> original, @Local(argsOnly = true) Mob ghast) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition(ghast, target);
        return alias.x;
    }

    @WrapOperation(
            method = "faceMovementDirection",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D")
    )
    private static double faceAliasTargetZ(LivingEntity target, Operation<Double> original, @Local(argsOnly = true) Mob ghast) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition(ghast, target);
        return alias.z;
    }
}
