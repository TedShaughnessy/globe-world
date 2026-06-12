package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import globe.world.util.CoordUtil;
import net.minecraft.server.level.ServerEntityGetter;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

@Mixin(ServerEntityGetter.class)
public interface ServerEntityGetterMixin {
    @WrapOperation(
            method = "getNearestEntity(Ljava/lang/Class;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;DDDLnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/entity/LivingEntity;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerEntityGetter;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            )
    )
    private <T extends LivingEntity> List<T> getTopologicalNearestClassCandidates(
            ServerEntityGetter getter,
            Class<T> type,
            AABB bb,
            Predicate<? super T> selector,
            Operation<List<T>> original,
            Class<? extends T> requestedType,
            TargetingConditions targetConditions,
            LivingEntity source,
            double sourceX,
            double sourceY,
            double sourceZ,
            AABB requestedBox) {
        return source == null
                ? original.call(getter, type, bb, selector)
                : ActorLocalTargets.targetsInActorRange(source, type, bb, selector);
    }

    @WrapOperation(
            method = "getNearestEntity(Lnet/minecraft/tags/TagKey;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;DDDLnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/entity/LivingEntity;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerEntityGetter;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            )
    )
    private List<LivingEntity> getTopologicalTaggedNearestCandidates(
            ServerEntityGetter getter,
            Class<LivingEntity> type,
            AABB bb,
            Predicate<? super LivingEntity> selector,
            Operation<List<LivingEntity>> original,
            TagKey<EntityType<?>> tag,
            TargetingConditions targetConditions,
            LivingEntity source,
            double sourceX,
            double sourceY,
            double sourceZ,
            AABB requestedBox) {
        return source == null
                ? original.call(getter, type, bb, selector)
                : ActorLocalTargets.targetsInActorRange(source, type, bb, selector);
    }

    @WrapOperation(
            method = "getNearbyEntities(Ljava/lang/Class;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerEntityGetter;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            )
    )
    private <T extends LivingEntity> List<T> getTopologicalNearbyCandidates(
            ServerEntityGetter getter,
            Class<T> type,
            AABB bb,
            Predicate<? super T> selector,
            Operation<List<T>> original,
            Class<T> requestedType,
            TargetingConditions targetConditions,
            LivingEntity source,
            AABB requestedBox) {
        return source == null
                ? original.call(getter, type, bb, selector)
                : ActorLocalTargets.targetsInActorRange(source, type, bb, selector);
    }

    @WrapOperation(
            method = "getNearbyPlayers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getX()D"
            )
    )
    private double useAliasXForNearbyPlayerBox(
            Player player,
            Operation<Double> original,
            TargetingConditions targetConditions,
            LivingEntity source,
            AABB bb) {
        if (source == null) {
            return original.call(player);
        }
        Vec3 alias = ActorLocalTargets.position(source, player);
        return alias.x;
    }

    @WrapOperation(
            method = "getNearbyPlayers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getZ()D"
            )
    )
    private double useAliasZForNearbyPlayerBox(
            Player player,
            Operation<Double> original,
            TargetingConditions targetConditions,
            LivingEntity source,
            AABB bb) {
        if (source == null) {
            return original.call(player);
        }
        Vec3 alias = ActorLocalTargets.position(source, player);
        return alias.z;
    }

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
        if (source != null) {
            return ActorLocalTargets.distanceToSqr(source, candidate);
        }
        return CoordUtil.wrappedDistanceSqr(candidate.level(), candidate.getX(), candidate.getY(), candidate.getZ(), x, y, z);
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
        if (source != null) {
            return ActorLocalTargets.distanceToSqr(source, candidate);
        }
        return CoordUtil.wrappedDistanceSqr(candidate.level(), candidate.getX(), candidate.getY(), candidate.getZ(), x, y, z);
    }
}
