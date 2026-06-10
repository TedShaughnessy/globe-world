package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Sensing.class)
public class SensingMixin {
    @WrapOperation(
            method = "hasLineOfSight",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z"
            )
    )
    private boolean useAliasLineOfSight(Mob mob, Entity target, Operation<Boolean> original) {
        if (original.call(mob, target)) {
            return true;
        }
        return ActorLocalTargets.aliasLineOfSight(mob, target);
    }
}
