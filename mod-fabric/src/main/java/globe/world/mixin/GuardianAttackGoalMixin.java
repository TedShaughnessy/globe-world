package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.AiAliasUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Guardian;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.entity.monster.Guardian$GuardianAttackGoal")
public class GuardianAttackGoalMixin {
    @Shadow
    @Final
    private Guardian guardian;

    @WrapOperation(
            method = "canContinueToUse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/Guardian;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private double useAliasDistance(Guardian guardian, Entity target, Operation<Double> original) {
        return AiAliasUtil.distanceToSqr(guardian, target);
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/Guardian;hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z"
            )
    )
    private boolean useAliasLineOfSight(Guardian guardian, Entity target, Operation<Boolean> original) {
        return original.call(guardian, target) || AiAliasUtil.aliasLineOfSight(this.guardian, target);
    }
}
