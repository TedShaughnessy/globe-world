package globe.world.config;

public class GlobeConfig {
    public static final int DEFAULT_TILE_SIZE_CHUNKS = 6;

    private static TilingSettings settings = TilingSettings.square(DEFAULT_TILE_SIZE_CHUNKS);

    public static void setTilingSettings(TilingSettings newSettings) {
        settings = newSettings.sanitized();
    }

    public static TilingSettings tilingSettings() {
        return settings;
    }

    public static boolean enabled() {
        return settings.enabled();
    }

    public static int tileSizeChunks() {
        return settings.tileSize();
    }

    public static int tileSizeBlocks() {
        return tileSizeChunks() * 16;
    }
}
