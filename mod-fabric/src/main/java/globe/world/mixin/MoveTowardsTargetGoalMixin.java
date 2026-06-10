package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MoveTowardsTargetGoal.class)
public class MoveTowardsTargetGoalMixin {
    @Shadow
    @Final
    private PathfinderMob mob;

    @WrapOperation(
            method = {"canUse", "canContinueToUse"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private double useAliasDistanceForTargetProximity(LivingEntity target, Entity mob, Operation<Double> original) {
        return ActorLocalTargets.distanceToSqr(mob, target);
    }

    @WrapOperation(
            method = "canUse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;position()Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 moveTowardNearestAlias(LivingEntity target, Operation<Vec3> original) {
        return ActorLocalTargets.nearestAliasPosition(this.mob, target);
    }
}
