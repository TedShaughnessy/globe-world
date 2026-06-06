package globe.world.client;

import globe.world.config.GlobeConfig;
import globe.world.config.TilingSettings;
import globe.world.config.TilingSettingsHolder;
import globe.world.network.GlobeWorldNetworking;
import globe.world.util.GlobeDayLength;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.levelgen.WorldGenSettings;

public final class GlobeClientTilingSettings {
    private GlobeClientTilingSettings() {
    }

    public static void applySyncedFromServer(TilingSettings settings) {
        GlobeConfig.setTilingSettings(settings.sanitized());
    }

    public static boolean canEditFromPauseMenu() {
        return Minecraft.getInstance().getSingleplayerServer() != null;
    }

    public static void setFromPauseMenu(TilingSettings settings) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return;
        }

        TilingSettings sanitized = settings.sanitized();
        TilingSettings previous = GlobeConfig.tilingSettings();
        GlobeConfig.setTilingSettings(sanitized);

        server.executeBlocking(() -> {
            WorldGenSettings worldGenSettings = server.getWorldGenSettings();
            ((TilingSettingsHolder) (Object) worldGenSettings).globeWorld$setTilingSettings(sanitized);
            worldGenSettings.setDirty();
            GlobeConfig.setTilingSettings(sanitized);
            if (previous.dayLengthMultiplier() != sanitized.dayLengthMultiplier()) {
                GlobeDayLength.applyToServer(server, sanitized);
            }
            GlobeWorldNetworking.broadcastSettings(server, sanitized);
        });
    }
}
