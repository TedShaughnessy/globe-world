package globe.world.util;

import globe.world.config.TilingSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.WorldClocks;

public final class GlobeDayLength {
    private GlobeDayLength() {
    }

    public static void applyToServer(MinecraftServer server, TilingSettings settings) {
        server.clockManager().setRate(
                server.registryAccess().getOrThrow(WorldClocks.OVERWORLD),
                clockRateFor(settings)
        );
    }

    private static float clockRateFor(TilingSettings settings) {
        return (float) (1.0D / settings.sanitized().dayLengthMultiplier());
    }
}
