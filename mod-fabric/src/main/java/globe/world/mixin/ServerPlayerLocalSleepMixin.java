package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.GlobeLocalDaylight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public class ServerPlayerLocalSleepMixin {
    @WrapOperation(
        method = "startSleepInBed",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"
        )
    )
    private boolean canSleepAtLocalNight(BedRule rule, Level level, Operation<Boolean> original, BlockPos pos) {
        return GlobeLocalDaylight.enabled(level)
                ? GlobeLocalDaylight.canSleep(rule, level, pos)
                : original.call(rule, level);
    }
}
