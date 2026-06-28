package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.entity.ActorLocalTargets;
import globe.world.util.GlobeCurvedProjectileAim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.jspecify.annotations.Nullable;

@Mixin(CrossbowItem.class)
public class CrossbowItemRangedAttackMixin {
    @WrapOperation(
            method = "shootProjectile",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/Projectile;shoot(DDDFF)V"
            )
    )
    private void shootPlayerCrossbowFromCurvedVisualAim(
            Projectile projectile,
            double x,
            double y,
            double z,
            float power,
            float uncertainty,
            Operation<Void> original,
            @Local(argsOnly = true, ordinal = 0) LivingEntity shooter,
            @Nullable @Local(argsOnly = true, ordinal = 1) LivingEntity targetOverride) {
        if (targetOverride == null
                && shooter instanceof Player
                && GlobeCurvedProjectileAim.shootFromVisualDirection(
                projectile,
                shooter,
                new Vec3(x, y, z),
                power,
                uncertainty,
                GlobeCurvedProjectileAim.DEFAULT_PLAYER_PROJECTILE_FOCUS_DISTANCE,
                GlobeCurvedProjectileAim.CLOSE_PLAYER_PROJECTILE_FOCUS_RANGE,
                1.0F)) {
            return;
        }

        original.call(projectile, x, y, z, power, uncertainty);
    }

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
