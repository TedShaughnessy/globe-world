package globe.world.util;

import globe.world.config.GlobeConfig;
import globe.world.config.TilingSettings;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class GlobeCurvature {
    private static final double DEFAULT_CURVATURE_DROP_CLAMP_RADIUS_MULTIPLIER = 32.0D;
    private static final double TINY_TILE_CURVATURE_DROP_CLAMP_RADIUS_MULTIPLIER = 512.0D;
    private static final double MIN_TINY_TILE_CURVATURE_DROP_CLAMP_DISTANCE_BLOCKS = 64.0D;
    private static final int SMALL_TILE_CURVATURE_LIMIT_CHUNKS = 15;
    private static final int TINY_TILE_CURVATURE_LIMIT_CHUNKS = 6;
    private static final float NORMAL_MAX_CURVATURE_SCALE = TilingSettings.CURVATURE_REALISTIC_SCALE;
    private static final float TINY_TILE_MAX_CURVATURE_SCALE = 24.0F;

    private GlobeCurvature() {
    }

    public static double curvatureRadius(Level level) {
        return curvatureRadius(DimensionTiling.forLevel(level), level.dimension());
    }

    public static double curvatureRadius(DimensionTiling tiling, ResourceKey<Level> dimension) {
        if (!tiling.enabled()) {
            return 0.0D;
        }

        return curvatureRadiusBlocks(tiling.tileSizeChunks(), GlobeConfig.curvaturePercent(dimension));
    }

    public static double curvatureRadiusBlocks(int tileSizeChunks, int curvaturePercent) {
        int sanitizedTileSizeChunks = Math.max(1, tileSizeChunks);
        float tileSize = sanitizedTileSizeChunks * 16.0F;
        float curvatureScale = curvatureScaleForTileSize(
                sanitizedTileSizeChunks,
                TilingSettings.curvatureScaleFromPercent(curvaturePercent)
        );
        if (curvatureScale <= 0.0F) {
            return 0.0D;
        }

        float radius = tileSize / curvatureScale;
        return sanitizedTileSizeChunks < SMALL_TILE_CURVATURE_LIMIT_CHUNKS ? radius : Math.max(radius, 16.0F);
    }

    public static double curvatureDrop(Level level, double distanceSqr) {
        return curvatureDrop(DimensionTiling.forLevel(level), level.dimension(), distanceSqr);
    }

    public static double curvatureDrop(DimensionTiling tiling, ResourceKey<Level> dimension, double distanceSqr) {
        double radius = curvatureRadius(tiling, dimension);
        if (radius <= 0.0D) {
            return 0.0D;
        }
        return Math.min(distanceSqr / (2.0D * radius), curvatureDropClamp(tiling, radius));
    }

    public static double curvatureDrop(double radius, DimensionTiling tiling, double distanceSqr) {
        if (radius <= 0.0D) {
            return 0.0D;
        }
        return Math.min(distanceSqr / (2.0D * radius), curvatureDropClamp(tiling, radius));
    }

    public static double curvatureDropClamp(DimensionTiling tiling, double radius) {
        return Math.max(radius * curvatureDropClampMultiplier(tiling), minimumTinyTileCurvatureDropClamp(tiling, radius));
    }

    private static float curvatureScaleForTileSize(int tileSizeChunks, float curvatureScale) {
        if (tileSizeChunks >= TINY_TILE_CURVATURE_LIMIT_CHUNKS) {
            return curvatureScale;
        }

        float tinyTileRange = TINY_TILE_CURVATURE_LIMIT_CHUNKS - 2.0F;
        float tileProgress = Math.max(0.0F, tileSizeChunks - 2.0F) / tinyTileRange;
        float maxCurvatureScale = TINY_TILE_MAX_CURVATURE_SCALE
                + (NORMAL_MAX_CURVATURE_SCALE - TINY_TILE_MAX_CURVATURE_SCALE) * tileProgress;
        return curvatureScale * (maxCurvatureScale / NORMAL_MAX_CURVATURE_SCALE);
    }

    private static double curvatureDropClampMultiplier(DimensionTiling tiling) {
        if (!tiling.enabled() || tiling.tileSizeChunks() >= TINY_TILE_CURVATURE_LIMIT_CHUNKS) {
            return DEFAULT_CURVATURE_DROP_CLAMP_RADIUS_MULTIPLIER;
        }

        double tinyTileRange = TINY_TILE_CURVATURE_LIMIT_CHUNKS - 2.0D;
        double tileProgress = Math.max(0.0D, tiling.tileSizeChunks() - 2.0D) / tinyTileRange;
        return TINY_TILE_CURVATURE_DROP_CLAMP_RADIUS_MULTIPLIER
                + (DEFAULT_CURVATURE_DROP_CLAMP_RADIUS_MULTIPLIER - TINY_TILE_CURVATURE_DROP_CLAMP_RADIUS_MULTIPLIER) * tileProgress;
    }

    private static double minimumTinyTileCurvatureDropClamp(DimensionTiling tiling, double radius) {
        if (!tiling.enabled() || tiling.tileSizeChunks() >= TINY_TILE_CURVATURE_LIMIT_CHUNKS || radius <= 0.0D) {
            return 0.0D;
        }

        return MIN_TINY_TILE_CURVATURE_DROP_CLAMP_DISTANCE_BLOCKS
                * MIN_TINY_TILE_CURVATURE_DROP_CLAMP_DISTANCE_BLOCKS
                / (2.0D * radius);
    }
}
