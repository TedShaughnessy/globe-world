package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.GlobeLocalDaylight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Monster.class)
public class MonsterLocalDaylightMixin {
    @WrapOperation(
        method = "isDarkEnoughToSpawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerLevelAccessor;getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;)I"
        )
    )
    private static int useLocalSkyDarkenForMonsterSpawning(
            ServerLevelAccessor level,
            BlockPos pos,
            Operation<Integer> original) {
        return GlobeLocalDaylight.enabled(level.getLevel())
                ? GlobeLocalDaylight.getMaxLocalRawBrightness(level.getLevel(), pos)
                : original.call(level, pos);
    }
}
