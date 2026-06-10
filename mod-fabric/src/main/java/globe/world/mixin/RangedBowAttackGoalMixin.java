package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RangedBowAttackGoal.class)
public class RangedBowAttackGoalMixin {
    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/Monster;distanceToSqr(DDD)D"
            )
    )
    private double useAliasDistanceForBowAttack(Monster mob, double x, double y, double z, Operation<Double> original) {
        return ActorLocalTargets.distanceToSqr(mob, x, y, z);
    }
}
