package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.Sensing;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TargetingConditions.class)
public class TargetingConditionsMixin {

    @WrapOperation(
        method = "test",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;distanceToSqr(DDD)D"
        )
    )
    private double wrapTargetDistance(LivingEntity source, double x, double y, double z, Operation<Double> original) {
        return CoordUtil.wrappedDistanceSqr(source.level(), source.getX(), source.getY(), source.getZ(), x, y, z);
    }

    @WrapOperation(
        method = "test",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/sensing/Sensing;hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z"
        )
    )
    private boolean wrapLineOfSightAcrossTiles(
            Sensing sensing,
            Entity target,
            Operation<Boolean> original,
            ServerLevel level,
            LivingEntity source,
            LivingEntity targetLiving) {
        if (original.call(sensing, target)) {
            return true;
        }
        return source != null && targetLiving != null
                && CoordUtil.wrappedDistanceSqrXZ(level, source.getX(), source.getZ(), targetLiving.getX(), targetLiving.getZ())
                < source.distanceToSqr(targetLiving);
    }
}
