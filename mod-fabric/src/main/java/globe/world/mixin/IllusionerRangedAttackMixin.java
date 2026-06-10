package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Illusioner.class)
public class IllusionerRangedAttackMixin {
    @WrapOperation(
            method = "performRangedAttack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D")
    )
    private double aimAtAliasX(LivingEntity target, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition((Entity)(Object)this, target);
        return alias.x;
    }

    @WrapOperation(
            method = "performRangedAttack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D")
    )
    private double aimAtAliasZ(LivingEntity target, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition((Entity)(Object)this, target);
        return alias.z;
    }
}
