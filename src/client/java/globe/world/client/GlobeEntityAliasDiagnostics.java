package globe.world.client;

import globe.world.util.GlobeEntityAliasMode;
import globe.world.util.GlobeEntityAliasing;

public final class GlobeEntityAliasDiagnostics {
    private static int submittedThisFrame;
    private static int culledThisFrame;
    private static int autoSkippedThisFrame;

    private GlobeEntityAliasDiagnostics() {
    }

    public static void beginFrame() {
        submittedThisFrame = 0;
        culledThisFrame = 0;
        autoSkippedThisFrame = 0;
    }

    public static void recordSubmitted() {
        submittedThisFrame++;
    }

    public static void recordCulled() {
        culledThisFrame++;
    }

    public static void recordAutoSkipped() {
        autoSkippedThisFrame++;
    }

    public static GlobeEntityAliasMode mode() {
        return GlobeEntityAliasing.mode();
    }

    public static int submittedThisFrame() {
        return submittedThisFrame;
    }

    public static int culledThisFrame() {
        return culledThisFrame;
    }

    public static int autoSkippedThisFrame() {
        return autoSkippedThisFrame;
    }
}
