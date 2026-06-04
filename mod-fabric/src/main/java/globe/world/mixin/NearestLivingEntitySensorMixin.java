package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.AiAliasUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

@Mixin(NearestLivingEntitySensor.class)
public class NearestLivingEntitySensorMixin {

    @WrapOperation(
        method = "doTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
        )
    )
    private <T extends LivingEntity> List<T> addWrappedPlayersToSensorCandidates(
            ServerLevel level,
            Class<T> entityClass,
            AABB box,
            Predicate<? super T> predicate,
            Operation<List<T>> original,
            ServerLevel tickLevel,
            LivingEntity body) {
        List<T> entities = original.call(level, entityClass, box, predicate);
        double followRange = body.getAttributeValue(Attributes.FOLLOW_RANGE);
        double followRangeSqr = followRange * followRange;

        for (ServerPlayer player : level.players()) {
            if (!entityClass.isInstance(player)) {
                continue;
            }
            T candidate = entityClass.cast(player);
            if (entities.contains(candidate) || !predicate.test(candidate)) {
                continue;
            }
            double distanceSqr = AiAliasUtil.distanceToSqr(body, player);
            if (distanceSqr <= followRangeSqr) {
                entities.add(candidate);
            }
        }

        return entities;
    }

    @WrapOperation(
        method = "doTick",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Comparator;comparingDouble(Ljava/util/function/ToDoubleFunction;)Ljava/util/Comparator;"
        )
    )
    private Comparator<LivingEntity> sortSensorCandidatesByAliasDistance(
            ToDoubleFunction<? super LivingEntity> keyExtractor,
            Operation<Comparator<LivingEntity>> original,
            ServerLevel level,
            LivingEntity body) {
        return Comparator.comparingDouble(candidate -> AiAliasUtil.distanceToSqr(body, candidate));
    }
}
