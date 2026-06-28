package globe.world.atlas;

import globe.world.util.DimensionTiling;

public final class GlobeAtlasSurvey {
    public static final int LARGE_TILE_CUTOFF_CHUNKS = 512;
    private static final double TRAVEL_TILE_FRACTION = 0.9D;
    private static final int MAX_TRAVEL_CHUNKS = 12800;

    private GlobeAtlasSurvey() {
    }

    public static boolean surveyMode(final DimensionTiling tiling) {
        return tiling.tileSizeChunks() > LARGE_TILE_CUTOFF_CHUNKS;
    }

    public static boolean surveyMode(final int tileSizeBlocks) {
        return tileSizeBlocks > LARGE_TILE_CUTOFF_CHUNKS * 16;
    }

    public static int travelChunks(final int tileSizeChunks) {
        return Math.min((int)Math.ceil(tileSizeChunks * (double)tileSizeChunks * TRAVEL_TILE_FRACTION), MAX_TRAVEL_CHUNKS);
    }
}
