package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.AiAliasUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TargetGoal.class)
public class TargetGoalMixin {
    @Shadow
    @Final
    protected Mob mob;

    @WrapOperation(
            method = "canContinueToUse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private double useAliasDistanceForTargetRetention(Mob mob, Entity target, Operation<Double> original) {
        return AiAliasUtil.distanceToSqr(mob, target);
    }

    @WrapOperation(
            method = "canReach",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getBlockX()I"
            )
    )
    private int comparePathEndToAliasTargetX(LivingEntity target, Operation<Integer> original) {
        BlockPos alias = AiAliasUtil.nearestAliasBlockPos(this.mob, target);
        return alias.getX();
    }

    @WrapOperation(
            method = "canReach",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getBlockZ()I"
            )
    )
    private int comparePathEndToAliasTargetZ(LivingEntity target, Operation<Integer> original) {
        BlockPos alias = AiAliasUtil.nearestAliasBlockPos(this.mob, target);
        return alias.getZ();
    }
}
