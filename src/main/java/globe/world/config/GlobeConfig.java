package globe.world.config;

public class GlobeConfig {
    public static final int DEFAULT_TILE_SIZE_CHUNKS = 100;

    private static volatile TilingSettings settings = TilingSettings.DEFAULT;
    private static volatile int settingsVersion = 0;

    public static void setTilingSettings(TilingSettings newSettings) {
        TilingSettings sanitized = newSettings.sanitized();
        if (!sanitized.equals(settings)) {
            settings = sanitized;
            settingsVersion++;
        }
    }

    public static TilingSettings tilingSettings() {
        return settings;
    }

    public static boolean enabled() {
        return settings.enabled();
    }

    public static int settingsVersion() {
        return settingsVersion;
    }

    public static int tileSizeChunks() {
        return settings.tileSize();
    }

    public static int tileSizeBlocks() {
        return tileSizeChunks() * 16;
    }

    public static int curvaturePercent() {
        return settings.curvaturePercent();
    }
}
