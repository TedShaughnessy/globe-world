package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanFreezeWhenLookedAt")
public class EndermanFreezeWhenLookedAtMixin {
    @Shadow
    @Final
    private EnderMan enderman;

    @WrapOperation(
            method = "canUse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private double useAliasDistanceForFreezeRange(LivingEntity target, Entity enderman, Operation<Double> original) {
        return ActorLocalTargets.distanceToSqr(this.enderman, target);
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getX()D"
            )
    )
    private double lookAtAliasTargetX(LivingEntity target, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.nearestAliasEyePosition(this.enderman, target);
        return alias.x;
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D"
            )
    )
    private double lookAtAliasTargetZ(LivingEntity target, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.nearestAliasEyePosition(this.enderman, target);
        return alias.z;
    }
}
