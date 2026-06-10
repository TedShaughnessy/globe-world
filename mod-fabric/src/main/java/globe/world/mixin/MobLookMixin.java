package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mob.class)
public class MobLookMixin {
    @WrapOperation(
            method = "lookAt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getX()D"
            )
    )
    private double lookAtNearestAliasX(Entity target, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition((Mob) (Object) this, target);
        return alias.x;
    }

    @WrapOperation(
            method = "lookAt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getZ()D"
            )
    )
    private double lookAtNearestAliasZ(Entity target, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition((Mob) (Object) this, target);
        return alias.z;
    }

    @WrapOperation(
            method = "isWithinMeleeAttackRange",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getHitbox()Lnet/minecraft/world/phys/AABB;"
            )
    )
    private AABB useNearestAliasHitboxForMeleeReach(LivingEntity target, Operation<AABB> original) {
        return ActorLocalTargets.nearestAliasHitbox((Mob) (Object) this, target, original.call(target));
    }
}
