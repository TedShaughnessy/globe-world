package globe.world.client;

import globe.world.config.GlobeConfig;
import globe.world.config.TilingSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.Locale;

public final class GlobeCurvatureShader {
    private static final double CURVATURE_DROP_CLAMP_DISTANCE_FACTOR = Math.sqrt(24.0D);
    private static final double RENDER_DISTANCE_SAFETY_MARGIN_BLOCKS = 16.0D;
    private static final String TERRAIN_POSITION_LINE = "    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;";
    private static final String BLOCK_POSITION_LINE = "    vec3 pos = Position + ModelOffset;";
    private static final String RAW_POSITION_LINE = "    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);";
    private static final String FOG_POSITION_VARIABLE = "globeWorld_fogPos";
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

        return source.replace("\nvoid main() {", helperSource() + "\nvoid main() {")
                .replace(TERRAIN_POSITION_LINE, curvePositionLine(TERRAIN_POSITION_LINE))
                .replace("fog_spherical_distance(pos)", "fog_spherical_distance(" + FOG_POSITION_VARIABLE + ")")
                .replace("fog_cylindrical_distance(pos)", "fog_cylindrical_distance(" + FOG_POSITION_VARIABLE + ")");
    }

    public static String transformWorldVertexShader(String source) {
        if (source.contains(TERRAIN_POSITION_LINE)) {
            return transformTerrainVertexShader(source);
        }

        String transformed = source;
        if (transformed.contains(BLOCK_POSITION_LINE)) {
            transformed = transformed.replace(BLOCK_POSITION_LINE, curvePositionLine(BLOCK_POSITION_LINE))
                    .replace("fog_spherical_distance(pos)", "fog_spherical_distance(" + FOG_POSITION_VARIABLE + ")")
                    .replace("fog_cylindrical_distance(pos)", "fog_cylindrical_distance(" + FOG_POSITION_VARIABLE + ")");
        } else if (transformed.contains(RAW_POSITION_LINE)) {
            transformed = transformed.replace(RAW_POSITION_LINE, "    vec3 pos = globeWorld_applyCurvature(Position);\n    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);");
        } else {
            return source;
        }

        transformed = transformed
                .replace("fog_spherical_distance(Position)", "fog_spherical_distance(globeWorld_fogPosition(Position))")
                .replace("fog_cylindrical_distance(Position)", "fog_cylindrical_distance(globeWorld_fogPosition(Position))");
        return transformed.replace("\nvoid main() {", helperSource() + "\nvoid main() {");
    }

    public static double curvatureRadius() {
        float tileSize = GlobeConfig.enabled() ? (float) GlobeConfig.tileSizeBlocks() : 0.0F;
        return curvatureRadius(tileSize);
    }

    public static double curvatureDrop(double distanceSqr) {
        double radius = curvatureRadius();
        if (radius <= 0.0D) {
            return 0.0D;
        }
        return Math.min(distanceSqr / (2.0D * radius), radius * 12.0D);
    }

    public static int curvatureRenderDistanceCapChunks() {
        double radius = curvatureRadius();
        if (radius <= 0.0D) {
            return Integer.MAX_VALUE;
        }

        double maxDistanceBlocks = radius * CURVATURE_DROP_CLAMP_DISTANCE_FACTOR - RENDER_DISTANCE_SAFETY_MARGIN_BLOCKS;
        return Math.max(2, (int) Math.floor(maxDistanceBlocks / (16.0D * Math.sqrt(2.0D))));
    }

    private static void reloadShadersWhenSettingsChange(Minecraft minecraft) {
        if (reloadQueued || loadedSettingsVersion == GlobeConfig.settingsVersion()) {
            return;
        }

        reloadQueued = true;
        loadedSettingsVersion = GlobeConfig.settingsVersion();
        minecraft.reloadResourcePacks().whenComplete((ignored, throwable) -> reloadQueued = false);
    }

    private static String helperSource() {
        return """

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

vec3 globeWorld_fogPosition(vec3 pos) {
    if (globeWorld_curvatureRadius() <= 0.0) {
        return pos;
    }

    return vec3(pos.x, 0.0, pos.z);
}
""".formatted(String.format(Locale.ROOT, "%.1f", curvatureRadius()));
    }

    private static String curvePositionLine(String positionLine) {
        return positionLine + "\n    vec3 " + FOG_POSITION_VARIABLE + " = globeWorld_fogPosition(pos);\n    pos = globeWorld_applyCurvature(pos);";
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
