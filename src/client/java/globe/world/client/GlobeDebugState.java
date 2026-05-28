package globe.world.client;

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
}
