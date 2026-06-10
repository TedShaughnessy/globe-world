package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import globe.world.util.MobNavigationAliasUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MeleeAttackGoal.class)
public class MeleeAttackGoalMixin {
    @Shadow
    @Final
    protected PathfinderMob mob;

    @Shadow
    private double pathedTargetX;

    @Shadow
    private double pathedTargetY;

    @Shadow
    private double pathedTargetZ;

    @Shadow
    private int ticksUntilNextPathRecalculation;

    @Inject(method = "tick", at = @At("HEAD"))
    private void recomputeAfterAliasCanonicalization(CallbackInfo ci) {
        if (MobNavigationAliasUtil.consumePathRecompute(this.mob)) {
            this.pathedTargetX = 0.0D;
            this.pathedTargetY = 0.0D;
            this.pathedTargetZ = 0.0D;
            this.ticksUntilNextPathRecalculation = 0;
        }
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;distanceToSqr(DDD)D"
            )
    )
    private double compareTargetToPathedAlias(LivingEntity target, double x, double y, double z, Operation<Double> original) {
        return ActorLocalTargets.distanceToSqr(target, x, y, z);
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getX()D"
            )
    )
    private double storeNearestAliasPathedTargetX(LivingEntity target, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition(this.mob, target);
        return alias.x;
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D"
            )
    )
    private double storeNearestAliasPathedTargetZ(LivingEntity target, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition(this.mob, target);
        return alias.z;
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/PathfinderMob;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private double useAliasDistanceForRecomputeDelay(PathfinderMob mob, Entity target, Operation<Double> original) {
        return ActorLocalTargets.distanceToSqr(mob, target);
    }
}
