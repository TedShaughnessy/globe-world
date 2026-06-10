package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WitherBoss.class)
public class WitherBossRangedAttackMixin {
    @WrapOperation(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private double useAliasDistanceForSideHead(WitherBoss wither, Entity target, Operation<Double> original) {
        return ActorLocalTargets.distanceToSqr(wither, target);
    }

    @WrapOperation(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z"
            )
    )
    private boolean useAliasLineOfSightForSideHead(WitherBoss wither, Entity target, Operation<Boolean> original) {
        return original.call(wither, target) || ActorLocalTargets.aliasLineOfSight(wither, target);
    }

    @WrapOperation(
            method = "performRangedAttack(ILnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D")
    )
    private double aimAtAliasX(LivingEntity target, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition((WitherBoss)(Object)this, target);
        return alias.x;
    }

    @WrapOperation(
            method = "performRangedAttack(ILnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D")
    )
    private double aimAtAliasZ(LivingEntity target, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition((WitherBoss)(Object)this, target);
        return alias.z;
    }
}
