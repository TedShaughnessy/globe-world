package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.util.GlobeLocalDaylight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PhantomSpawner.class)
public class PhantomSpawnerLocalDaylightMixin {
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getSkyDarken()I"
        )
    )
    private int enterPlayerLoopForLocalPhantomSpawning(ServerLevel level, Operation<Integer> original) {
        return GlobeLocalDaylight.enabled(level) ? 5 : original.call(level);
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/DifficultyInstance;isHarderThan(F)Z"
        )
    )
    private boolean requireLocalDarknessForPhantomSpawning(
            DifficultyInstance difficulty,
            float value,
            Operation<Boolean> original,
            @Local(argsOnly = true) ServerLevel level,
            @Local BlockPos playerPos) {
        if (GlobeLocalDaylight.enabled(level) && GlobeLocalDaylight.skyDarken(level, playerPos) < 5) {
            return false;
        }

        return original.call(difficulty, value);
    }
}
