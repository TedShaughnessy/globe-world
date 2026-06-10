package globe.world.diagnostics;

import globe.world.GlobeWorld;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class GlobeDiagnostics {
    private static final Set<DiagnosticsChannel> ENABLED = Collections.synchronizedSet(EnumSet.noneOf(DiagnosticsChannel.class));

    private GlobeDiagnostics() {
    }

    public static boolean enabled(DiagnosticsChannel channel) {
        return ENABLED.contains(channel);
    }

    public static void setEnabled(DiagnosticsChannel channel, boolean enabled) {
        if (enabled) {
            ENABLED.add(channel);
        } else {
            ENABLED.remove(channel);
        }
    }

    public static void clear() {
        ENABLED.clear();
    }

    public static void debug(DiagnosticsChannel channel, String message, Object... args) {
        if (enabled(channel)) {
            GlobeWorld.LOGGER.debug(message, args);
        }
    }

    public static void warn(DiagnosticsChannel channel, String message, Object... args) {
        if (enabled(channel)) {
            GlobeWorld.LOGGER.warn(message, args);
        }
    }
}
