package globe.world.client;

import globe.world.config.GlobeSettings;

public final class GlobeWorldCreateState {
    private static GlobeSettings settings = GlobeSettings.DEFAULT;

    private GlobeWorldCreateState() {
    }

    public static GlobeSettings get() {
        return settings;
    }

    public static void set(GlobeSettings newSettings) {
        settings = newSettings == null ? GlobeSettings.DEFAULT : newSettings;
    }
}
