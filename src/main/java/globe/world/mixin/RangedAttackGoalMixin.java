package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.AiAliasUtil;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RangedAttackGoal.class)
public class RangedAttackGoalMixin {
    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;distanceToSqr(DDD)D"
            )
    )
    private double useAliasDistanceForRangedAttack(Mob mob, double x, double y, double z, Operation<Double> original) {
        return AiAliasUtil.distanceToSqr(mob, x, y, z);
    }
}
