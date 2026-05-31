package globe.world.client;

import globe.world.util.GlobeEntityAliasMode;
import globe.world.util.GlobeEntityAliasing;

public final class GlobeDebugState {
    private static boolean debugScreenEnabled;
    private static boolean tileBordersEnabled;

    private GlobeDebugState() {
    }

    public static boolean debugScreenEnabled() {
        return debugScreenEnabled;
    }

    public static boolean toggleDebugScreen() {
        debugScreenEnabled = !debugScreenEnabled;
        return debugScreenEnabled;
    }

    public static boolean tileBordersEnabled() {
        return tileBordersEnabled;
    }

    public static boolean toggleTileBorders() {
        tileBordersEnabled = !tileBordersEnabled;
        return tileBordersEnabled;
    }

    public static GlobeEntityAliasMode cycleEntityAliasMode() {
        return GlobeEntityAliasing.cycleMode();
    }

    public static int cycleEntityAliasRingLimit() {
        return GlobeEntityAliasing.cycleMaxAliasRings();
    }

    public static String entityAliasRingLimitDisplayName() {
        return GlobeEntityAliasing.maxAliasRingsDisplayName();
    }
}
