package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.entity.monster.Phantom$PhantomAttackStrategyGoal")
public class PhantomAttackStrategyGoalMixin {
    @Shadow(remap = false)
    @Final
    private Phantom this$0;

    @WrapOperation(
            method = "setAnchorAboveTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;blockPosition()Lnet/minecraft/core/BlockPos;"
            )
    )
    private BlockPos useAliasTargetAnchor(LivingEntity target, Operation<BlockPos> original) {
        return ActorLocalTargets.nearestAliasBlockPos(this.this$0, target);
    }
}
