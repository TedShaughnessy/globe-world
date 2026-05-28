package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.GlobeLocalDaylight;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.OptionalLong;

@Mixin(ServerLevel.class)
public class ServerLevelLocalSleepTimeMixin {
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/clock/ServerClockManager;moveToTimeMarker(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;)Z"
        )
    )
    private boolean moveToLocalMorningAfterSleep(
            ServerClockManager clockManager,
            Holder<WorldClock> clock,
            ResourceKey<ClockTimeMarker> timeMarker,
            Operation<Boolean> original) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (timeMarker.equals(ClockTimeMarkers.WAKE_UP_FROM_SLEEP) && GlobeLocalDaylight.enabled(level)) {
            OptionalLong wakeTime = GlobeLocalDaylight.sleepWakeTime(level, clock);
            if (wakeTime.isPresent()) {
                clockManager.setTotalTicks(clock, wakeTime.getAsLong());
                return true;
            }
        }

        return original.call(clockManager, clock, timeMarker);
    }
}
