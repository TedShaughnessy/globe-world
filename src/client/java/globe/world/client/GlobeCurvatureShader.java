package globe.world.client;

import globe.world.config.GlobeConfig;
import globe.world.config.TilingSettings;
import globe.world.util.DimensionTiling;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Locale;

public final class GlobeCurvatureShader {
    private static final double DEFAULT_CURVATURE_DROP_CLAMP_RADIUS_MULTIPLIER = 32.0D;
    private static final double TINY_TILE_CURVATURE_DROP_CLAMP_RADIUS_MULTIPLIER = 512.0D;
    private static final double MIN_TINY_TILE_CURVATURE_DROP_CLAMP_DISTANCE_BLOCKS = 64.0D;
    private static final double RENDER_DISTANCE_SAFETY_MARGIN_BLOCKS = 16.0D;
    private static final int SMALL_TILE_CURVATURE_LIMIT_CHUNKS = 15;
    private static final int TINY_TILE_CURVATURE_LIMIT_CHUNKS = 6;
    private static final float NORMAL_MAX_CURVATURE_SCALE = TilingSettings.CURVATURE_REALISTIC_SCALE;
    private static final float TINY_TILE_MAX_CURVATURE_SCALE = 24.0F;
    private static final int TINY_TILE_FOG_LIMIT_CHUNKS = 6;
    private static final float MIN_TINY_TILE_FOG_DISTANCE_SCALE = 0.65F;
    private static final String TERRAIN_POSITION_LINE = "    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;";
    private static final String BLOCK_POSITION_LINE = "    vec3 pos = Position + ModelOffset;";
    private static final String CLOUD_POSITION_LINE = "    vec3 pos = (faceVertex * CellSize) + (vec3(cellX, 0, cellZ) * CellSize) + CloudOffset;";
    private static final String RAW_POSITION_LINE = "    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);";
    private static final String FOG_POSITION_VARIABLE = "globeWorld_fogPos";
    private static int loadedSettingsVersion = GlobeConfig.settingsVersion();
    private static ResourceKey<Level> loadedDimension = Level.OVERWORLD;
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
        } else if (transformed.contains(CLOUD_POSITION_LINE)) {
            transformed = transformed.replace(
                            CLOUD_POSITION_LINE,
                            CLOUD_POSITION_LINE + "\n    vec3 " + FOG_POSITION_VARIABLE + " = globeWorld_fogPosition(pos);\n    pos = globeWorld_applyCloudCurvature(pos);"
                    )
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
        return configuredCurvatureRadius();
    }

    public static double curvatureDrop(double distanceSqr) {
        double radius = curvatureRadius();
        if (radius <= 0.0D) {
            return 0.0D;
        }
        return Math.min(distanceSqr / (2.0D * radius), curvatureDropClamp(radius));
    }

    public static int curvatureRenderDistanceCapChunks() {
        double radius = curvatureRadius();
        if (radius <= 0.0D) {
            return Integer.MAX_VALUE;
        }

        double maxDistanceBlocks = curvatureDropClampDistance(radius) - RENDER_DISTANCE_SAFETY_MARGIN_BLOCKS;
        return Math.max(2, (int) Math.floor(maxDistanceBlocks / (16.0D * Math.sqrt(2.0D))));
    }

    private static void reloadShadersWhenSettingsChange(Minecraft minecraft) {
        ResourceKey<Level> currentDimension = minecraft.level == null ? Level.OVERWORLD : minecraft.level.dimension();
        if (reloadQueued
                || loadedSettingsVersion == GlobeConfig.settingsVersion()
                && loadedDimension.equals(currentDimension)) {
            return;
        }

        reloadQueued = true;
        loadedSettingsVersion = GlobeConfig.settingsVersion();
        loadedDimension = currentDimension;
        minecraft.reloadResourcePacks().whenComplete((ignored, throwable) -> reloadQueued = false);
    }

    private static String helperSource() {
        return """

float globeWorld_curvatureRadius() {
    return %s;
}

float globeWorld_curvatureDropClampMultiplier() {
    return %s;
}

float globeWorld_curvatureDropClamp() {
    return %s;
}

float globeWorld_fogDistanceScale() {
    return %s;
}

vec3 globeWorld_applyCurvature(vec3 pos) {
    float radius = globeWorld_curvatureRadius();
    if (radius <= 0.0) {
        return pos;
    }

    float distanceSqr = dot(pos.xz, pos.xz);
    float drop = min(distanceSqr / (2.0 * radius), globeWorld_curvatureDropClamp());
    pos.y -= drop;
    return pos;
}

vec3 globeWorld_applyCloudCurvature(vec3 pos) {
    float radius = globeWorld_curvatureRadius();
    if (radius <= 0.0) {
        return pos;
    }

    float cloudRadius = (radius + max(pos.y, 0.0)) * 2.0; // doubling makes it less distracting
    float distanceSqr = dot(pos.xz, pos.xz);
    float drop = min(distanceSqr / (2.0 * cloudRadius), globeWorld_curvatureDropClamp());
    pos.y -= drop;
    return pos;
}

vec3 globeWorld_fogPosition(vec3 pos) {
    if (globeWorld_curvatureRadius() <= 0.0) {
        return pos;
    }

    return vec3(pos.x * globeWorld_fogDistanceScale(), 0.0, pos.z * globeWorld_fogDistanceScale());
}
""".formatted(
                String.format(Locale.ROOT, "%.1f", configuredCurvatureRadius()),
                String.format(Locale.ROOT, "%.1f", curvatureDropClampMultiplier()),
                String.format(Locale.ROOT, "%.1f", curvatureDropClamp(configuredCurvatureRadius())),
                String.format(Locale.ROOT, "%.3f", fogDistanceScale())
        );
    }

    private static String curvePositionLine(String positionLine) {
        return positionLine + "\n    vec3 " + FOG_POSITION_VARIABLE + " = globeWorld_fogPosition(pos);\n    pos = globeWorld_applyCurvature(pos);";
    }

    private static float configuredCurvatureRadius() {
        DimensionTiling tiling = currentTiling();
        float tileSize = (float) tiling.tileSizeBlocks();
        if (!tiling.enabled() || tileSize <= 0.0F) {
            return 0.0F;
        }

        float curvatureScale = curvatureScaleForTile(tiling);
        if (curvatureScale <= 0.0F) {
            return 0.0F;
        }

        float radius = tileSize / curvatureScale;
        return tiling.tileSizeChunks() < SMALL_TILE_CURVATURE_LIMIT_CHUNKS ? radius : Math.max(radius, 16.0F);
    }

    private static float curvatureScaleForTile(DimensionTiling tiling) {
        Minecraft minecraft = Minecraft.getInstance();
        int curvaturePercent = minecraft.level == null
                ? GlobeConfig.curvaturePercent()
                : GlobeConfig.curvaturePercent(minecraft.level.dimension());
        float curvatureScale = TilingSettings.curvatureScaleFromPercent(curvaturePercent);
        int tileSizeChunks = tiling.tileSizeChunks();
        if (tileSizeChunks >= TINY_TILE_CURVATURE_LIMIT_CHUNKS) {
            return curvatureScale;
        }

        float tinyTileRange = TINY_TILE_CURVATURE_LIMIT_CHUNKS - 2.0F;
        float tileProgress = Math.max(0.0F, tileSizeChunks - 2.0F) / tinyTileRange;
        float maxCurvatureScale = TINY_TILE_MAX_CURVATURE_SCALE
                + (NORMAL_MAX_CURVATURE_SCALE - TINY_TILE_MAX_CURVATURE_SCALE) * tileProgress;
        return curvatureScale * (maxCurvatureScale / NORMAL_MAX_CURVATURE_SCALE);
    }

    private static float fogDistanceScale() {
        DimensionTiling tiling = currentTiling();
        if (!tiling.enabled() || tiling.tileSizeChunks() >= TINY_TILE_FOG_LIMIT_CHUNKS) {
            return 1.0F;
        }

        float tinyTileRange = TINY_TILE_FOG_LIMIT_CHUNKS - 2.0F;
        float tileProgress = Math.max(0.0F, tiling.tileSizeChunks() - 2.0F) / tinyTileRange;
        return MIN_TINY_TILE_FOG_DISTANCE_SCALE + (1.0F - MIN_TINY_TILE_FOG_DISTANCE_SCALE) * tileProgress;
    }

    private static double curvatureDropClampMultiplier() {
        DimensionTiling tiling = currentTiling();
        if (!tiling.enabled() || tiling.tileSizeChunks() >= TINY_TILE_CURVATURE_LIMIT_CHUNKS) {
            return DEFAULT_CURVATURE_DROP_CLAMP_RADIUS_MULTIPLIER;
        }

        double tinyTileRange = TINY_TILE_CURVATURE_LIMIT_CHUNKS - 2.0D;
        double tileProgress = Math.max(0.0D, tiling.tileSizeChunks() - 2.0D) / tinyTileRange;
        return TINY_TILE_CURVATURE_DROP_CLAMP_RADIUS_MULTIPLIER
                + (DEFAULT_CURVATURE_DROP_CLAMP_RADIUS_MULTIPLIER - TINY_TILE_CURVATURE_DROP_CLAMP_RADIUS_MULTIPLIER) * tileProgress;
    }

    private static double curvatureDropClamp(double radius) {
        return Math.max(radius * curvatureDropClampMultiplier(), minimumTinyTileCurvatureDropClamp(radius));
    }

    private static double curvatureDropClampDistance(double radius) {
        return Math.sqrt(2.0D * radius * curvatureDropClamp(radius));
    }

    private static double minimumTinyTileCurvatureDropClamp(double radius) {
        DimensionTiling tiling = currentTiling();
        if (!tiling.enabled() || tiling.tileSizeChunks() >= TINY_TILE_CURVATURE_LIMIT_CHUNKS || radius <= 0.0D) {
            return 0.0D;
        }

        return MIN_TINY_TILE_CURVATURE_DROP_CLAMP_DISTANCE_BLOCKS
                * MIN_TINY_TILE_CURVATURE_DROP_CLAMP_DISTANCE_BLOCKS
                / (2.0D * radius);
    }

    private static DimensionTiling currentTiling() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return DimensionTiling.forDimension(Level.OVERWORLD);
        }
        return DimensionTiling.forLevel(minecraft.level);
    }
}
