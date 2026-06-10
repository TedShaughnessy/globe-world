package globe.world.client;

import globe.world.config.GlobeConfig;
import globe.world.config.GlobeSettings;
import globe.world.config.GlobeSettingsHolder;
import globe.world.network.GlobeWorldNetworking;
import globe.world.util.GlobeDayLength;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.levelgen.WorldGenSettings;

public final class GlobeClientSettings {
    private GlobeClientSettings() {
    }

    public static void applySyncedFromServer(GlobeSettings settings) {
        GlobeConfig.setGlobeSettings(settings);
    }

    public static boolean canEditFromPauseMenu() {
        return Minecraft.getInstance().getSingleplayerServer() != null;
    }

    public static void setFromPauseMenu(GlobeSettings settings) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return;
        }

        GlobeSettings sanitized = settings == null ? GlobeSettings.DEFAULT : settings;
        GlobeSettings previous = GlobeConfig.globeSettings();
        GlobeConfig.setGlobeSettings(sanitized);

        server.executeBlocking(() -> {
            WorldGenSettings worldGenSettings = server.getWorldGenSettings();
            ((GlobeSettingsHolder) (Object) worldGenSettings).globeWorld$setGlobeSettings(sanitized);
            worldGenSettings.setDirty();
            GlobeConfig.setGlobeSettings(sanitized);
            if (previous.gameplay().dayLengthMultiplier() != sanitized.gameplay().dayLengthMultiplier()) {
                GlobeDayLength.applyToServer(server, sanitized.gameplay());
            }
            GlobeWorldNetworking.broadcastSettings(server, sanitized);
        });
    }
}
