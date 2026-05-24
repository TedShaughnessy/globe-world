package globe.world.client;

public final class GlobeDebugState {
    private static boolean tileBordersEnabled;

    private GlobeDebugState() {
    }

    public static boolean tileBordersEnabled() {
        return tileBordersEnabled;
    }

    public static boolean toggleTileBorders() {
        tileBordersEnabled = !tileBordersEnabled;
        return tileBordersEnabled;
    }
}
