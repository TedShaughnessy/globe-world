package globe.world.client;

import globe.world.config.GlobeConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.Locale;

public final class GlobeCurvatureShader {
    private static final String TERRAIN_POSITION_LINE = "    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;";
    private static int loadedSettingsVersion = GlobeConfig.settingsVersion();
    private static boolean reloadQueued = false;

    private GlobeCurvatureShader() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(GlobeCurvatureShader::reloadShadersWhenSettingsChange);
    }

    public static String transformTerrainVertexShader(String source) {
        if (!source.contains(TERRAIN_POSITION_LINE)) {
            return source;
        }

        float tileSize = GlobeConfig.enabled() ? (float) GlobeConfig.tileSizeBlocks() : 0.0F;
        String helper = """

float globeWorld_curvatureTileSize() {
    return %s;
}

vec3 globeWorld_applyCurvature(vec3 pos) {
    float tileSize = globeWorld_curvatureTileSize();
    if (tileSize <= 0.0) {
        return pos;
    }

    float radius = max(tileSize / 6, 16.0);
    float distanceSqr = dot(pos.xz, pos.xz);
    float drop = min(distanceSqr / (2.0 * radius), tileSize * 2.0);
    pos.y -= drop;
    return pos;
}
""".formatted(String.format(Locale.ROOT, "%.1f", tileSize));

        return source.replace("\nvoid main() {", helper + "\nvoid main() {")
                .replace(TERRAIN_POSITION_LINE, TERRAIN_POSITION_LINE + "\n    pos = globeWorld_applyCurvature(pos);");
    }

    private static void reloadShadersWhenSettingsChange(Minecraft minecraft) {
        if (reloadQueued || loadedSettingsVersion == GlobeConfig.settingsVersion()) {
            return;
        }

        reloadQueued = true;
        loadedSettingsVersion = GlobeConfig.settingsVersion();
        minecraft.reloadResourcePacks().whenComplete((ignored, throwable) -> reloadQueued = false);
    }
}
