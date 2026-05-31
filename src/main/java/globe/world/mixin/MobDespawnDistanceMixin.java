package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mob.class)
public class MobDespawnDistanceMixin {
    @WrapOperation(
            method = "checkDespawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private double useWrappedPlayerDistanceForDespawn(Entity player, Entity mob, Operation<Double> original) {
        return CoordUtil.wrappedDistanceSqr(
                player.level(),
                player.getX(),
                player.getY(),
                player.getZ(),
                mob.getX(),
                mob.getY(),
                mob.getZ());
    }
}
