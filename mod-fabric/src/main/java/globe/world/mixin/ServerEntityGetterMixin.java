package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.server.level.ServerEntityGetter;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ServerEntityGetter.class)
public interface ServerEntityGetterMixin {
    @WrapOperation(
            method = "getNearestEntity(Lnet/minecraft/tags/TagKey;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;DDDLnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/entity/LivingEntity;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;distanceToSqr(DDD)D"
            )
    )
    private double useAliasDistanceForTaggedNearestEntity(
            LivingEntity candidate,
            double x,
            double y,
            double z,
            Operation<Double> original,
            TagKey<EntityType<?>> tag,
            TargetingConditions targetConditions,
            LivingEntity source,
            double sourceX,
            double sourceY,
            double sourceZ,
            AABB bb) {
        return source == null ? original.call(candidate, x, y, z) : ActorLocalTargets.distanceToSqr(source, candidate);
    }

    @WrapOperation(
            method = "getNearestEntity(Ljava/util/List;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;DDD)Lnet/minecraft/world/entity/LivingEntity;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;distanceToSqr(DDD)D"
            )
    )
    private double useAliasDistanceForNearestEntity(
            LivingEntity candidate,
            double x,
            double y,
            double z,
            Operation<Double> original,
            List<? extends LivingEntity> entities,
            TargetingConditions targetConditions,
            LivingEntity source,
            double sourceX,
            double sourceY,
            double sourceZ) {
        return source == null ? original.call(candidate, x, y, z) : ActorLocalTargets.distanceToSqr(source, candidate);
    }
}
