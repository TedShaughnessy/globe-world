package globe.world.client;

import globe.world.config.TilingSettings;

public final class GlobeWorldCreateState {
    private static TilingSettings settings = TilingSettings.DEFAULT;

    private GlobeWorldCreateState() {
    }

    public static TilingSettings get() {
        return settings;
    }

    public static void set(TilingSettings newSettings) {
        settings = newSettings.sanitized();
    }
}
