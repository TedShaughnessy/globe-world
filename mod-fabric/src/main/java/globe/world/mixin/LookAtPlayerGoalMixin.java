package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LookAtPlayerGoal.class)
public class LookAtPlayerGoalMixin {
    @Shadow
    protected Mob mob;

    @WrapOperation(
            method = "canContinueToUse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private double useAliasDistanceForLookRetention(Mob mob, Entity lookAt, Operation<Double> original) {
        return ActorLocalTargets.distanceToSqr(mob, lookAt);
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getX()D"
            )
    )
    private double lookAtNearestAliasX(Entity lookAt, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.position(this.mob, lookAt);
        return alias.x;
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getZ()D"
            )
    )
    private double lookAtNearestAliasZ(Entity lookAt, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.position(this.mob, lookAt);
        return alias.z;
    }
}
