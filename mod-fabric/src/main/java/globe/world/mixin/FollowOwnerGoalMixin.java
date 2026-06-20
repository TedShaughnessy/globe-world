package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FollowOwnerGoal.class)
public class FollowOwnerGoalMixin {
    @WrapOperation(
            method = {"canUse", "canContinueToUse"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/TamableAnimal;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private double globeWorld$useAliasOwnerDistance(TamableAnimal tamable, Entity owner, Operation<Double> original) {
        return ActorLocalTargets.distanceToSqr(tamable, owner);
    }
}
