package globe.world.client;

import globe.world.config.GlobeConfig;
import globe.world.config.TilingSettings;
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

float globeWorld_curvatureRadius() {
    return %s;
}

vec3 globeWorld_applyCurvature(vec3 pos) {
    float radius = globeWorld_curvatureRadius();
    if (radius <= 0.0) {
        return pos;
    }

    float distanceSqr = dot(pos.xz, pos.xz);
    float drop = min(distanceSqr / (2.0 * radius), radius * 12.0);
    pos.y -= drop;
    return pos;
}
""".formatted(String.format(Locale.ROOT, "%.1f", curvatureRadius(tileSize)));

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

    private static float curvatureRadius(float tileSize) {
        if (!GlobeConfig.enabled() || tileSize <= 0.0F) {
            return 0.0F;
        }

        float curvatureScale = TilingSettings.curvatureScaleFromPercent(GlobeConfig.curvaturePercent());
        if (curvatureScale <= 0.0F) {
            return 0.0F;
        }
        return Math.max(tileSize / curvatureScale, 16.0F);
    }
}
