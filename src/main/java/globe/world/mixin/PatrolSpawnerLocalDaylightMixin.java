package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.util.GlobeLocalDaylight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PatrolSpawner.class)
public class PatrolSpawnerLocalDaylightMixin {
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;isBrightOutside()Z"
        )
    )
    private boolean enterPatrolSpawnPathForLocalDay(ServerLevel level, Operation<Boolean> original) {
        return GlobeLocalDaylight.enabled(level) || original.call(level);
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/attribute/EnvironmentAttributeSystem;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;Lnet/minecraft/core/BlockPos;)Ljava/lang/Object;"
        )
    )
    private Object requireLocalDayForPatrolSpawn(
            EnvironmentAttributeSystem attributes,
            EnvironmentAttribute<?> attribute,
            BlockPos pos,
            Operation<Object> original,
            @Local(argsOnly = true) ServerLevel level) {
        Object value = original.call(attributes, attribute, pos);
        if (attribute == EnvironmentAttributes.CAN_PILLAGER_PATROL_SPAWN && value instanceof Boolean canSpawn) {
            return canSpawn && GlobeLocalDaylight.canPatrolSpawnAt(level, pos);
        }

        return value;
    }
}
