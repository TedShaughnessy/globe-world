package globe.world.util;

public final class GlobeDistanceCaps {
    public static final int SMALL_TILE_RENDER_CAP_THRESHOLD_CHUNKS = 32;
    public static final int SMALL_TILE_RENDER_CAP_MIN_CURVATURE_PERCENT = 50;
    private static final int MIN_CAP_CHUNKS = 2;

    private GlobeDistanceCaps() {
    }

    public static int effectiveSimulationDistance(DimensionTiling tiling, int configuredDistance) {
        if (!tiling.enabled()) {
            return configuredDistance;
        }

        return Math.min(configuredDistance, simulationDistanceCap(tiling));
    }

    public static int simulationDistanceCap(DimensionTiling tiling) {
        if (!tiling.enabled()) {
            return Integer.MAX_VALUE;
        }

        return Math.max(MIN_CAP_CHUNKS, Math.ceilDiv(tiling.tileSizeChunks(), 2));
    }

    public static int effectiveRenderDistance(
            DimensionTiling tiling,
            int curvaturePercent,
            int configuredEffectiveDistance,
            int curvatureHorizonCap
    ) {
        if (!tiling.enabled()) {
            return configuredEffectiveDistance;
        }

        int effectiveDistance = Math.min(configuredEffectiveDistance, curvatureHorizonCap);
        if (tiling.tileSizeChunks() < SMALL_TILE_RENDER_CAP_THRESHOLD_CHUNKS
                && curvaturePercent >= SMALL_TILE_RENDER_CAP_MIN_CURVATURE_PERCENT) {
            effectiveDistance = Math.min(effectiveDistance, smallTileRenderDistanceCap(tiling));
        }
        return effectiveDistance;
    }

    public static int smallTileRenderDistanceCap(DimensionTiling tiling) {
        if (!tiling.enabled()) {
            return Integer.MAX_VALUE;
        }

        return Math.max(MIN_CAP_CHUNKS, tiling.tileSizeChunks());
    }
}
