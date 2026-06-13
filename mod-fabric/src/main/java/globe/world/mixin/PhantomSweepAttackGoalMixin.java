package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.entity.monster.Phantom$PhantomSweepAttackGoal")
public class PhantomSweepAttackGoalMixin {
    @Shadow(remap = false)
    @Final
    private Phantom this$0;

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getX()D"
            )
    )
    private double useAliasTargetX(LivingEntity target, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition(this.this$0, target);
        return alias.x;
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D"
            )
    )
    private double useAliasTargetZ(LivingEntity target, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition(this.this$0, target);
        return alias.z;
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
            )
    )
    private AABB useAliasTargetBox(LivingEntity target, Operation<AABB> original) {
        return ActorLocalTargets.box(this.this$0, target);
    }
}
