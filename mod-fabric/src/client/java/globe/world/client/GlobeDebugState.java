package globe.world.client;

public final class GlobeDebugState {
    private static boolean debugScreenEnabled;

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
        return debugScreenEnabled;
    }
}
