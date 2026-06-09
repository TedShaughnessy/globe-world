package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.util.AiAliasUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.breeze.Shoot;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Shoot.class)
public class BreezeShootMixin {
    @Inject(method = "isTargetWithinRange", at = @At("HEAD"), cancellable = true)
    private static void useAliasDistanceForRange(Breeze body, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (AiAliasUtil.canAlias(body, target)) {
            cir.setReturnValue(AiAliasUtil.distanceToSqr(body, target) < 256.0);
        }
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;position()Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 lookAtAlias(LivingEntity target, Operation<Vec3> original, @Local(argsOnly = true) Breeze breeze) {
        return AiAliasUtil.nearestAliasPosition(breeze, target);
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D")
    )
    private double aimAtAliasX(LivingEntity target, Operation<Double> original, @Local(argsOnly = true) Breeze breeze) {
        Vec3 alias = AiAliasUtil.nearestAliasPosition(breeze, target);
        return alias.x;
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D")
    )
    private double aimAtAliasZ(LivingEntity target, Operation<Double> original, @Local(argsOnly = true) Breeze breeze) {
        Vec3 alias = AiAliasUtil.nearestAliasPosition(breeze, target);
        return alias.z;
    }
}
