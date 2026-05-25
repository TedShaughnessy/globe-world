package globe.world.client;

import globe.world.config.GlobeConfig;
import globe.world.config.TilingSettings;
import globe.world.config.TilingSettingsHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.levelgen.WorldGenSettings;

public final class GlobeClientTilingSettings {
    private GlobeClientTilingSettings() {
    }

    public static void setFromPauseMenu(TilingSettings settings) {
        TilingSettings sanitized = settings.sanitized();
        GlobeConfig.setTilingSettings(sanitized);

        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return;
        }

        server.executeBlocking(() -> {
            WorldGenSettings worldGenSettings = server.getWorldGenSettings();
            ((TilingSettingsHolder) (Object) worldGenSettings).globeWorld$setTilingSettings(sanitized);
            worldGenSettings.setDirty();
            GlobeConfig.setTilingSettings(sanitized);
        });
    }
}
