package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.AiAliasUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.entity.monster.Shulker$ShulkerAttackGoal")
public class ShulkerAttackGoalMixin {
    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/Shulker;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private double useAliasDistance(Shulker shulker, Entity target, Operation<Double> original) {
        return AiAliasUtil.distanceToSqr(shulker, target);
    }
}
