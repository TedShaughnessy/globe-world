package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CrossbowItem.class)
public class CrossbowItemRangedAttackMixin {
    @WrapOperation(
            method = "shootProjectile",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D", ordinal = 0)
    )
    private double aimAtAliasX(
            LivingEntity target,
            Operation<Double> original,
            @Local(argsOnly = true, ordinal = 0) LivingEntity shooter) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition(shooter, target);
        return alias.x;
    }

    @WrapOperation(
            method = "shootProjectile",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D", ordinal = 0)
    )
    private double aimAtAliasZ(
            LivingEntity target,
            Operation<Double> original,
            @Local(argsOnly = true, ordinal = 0) LivingEntity shooter) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition(shooter, target);
        return alias.z;
    }
}
