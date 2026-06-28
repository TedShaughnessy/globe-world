package globe.world.atlas;

import globe.world.util.DimensionTiling;

public final class GlobeAtlasSurvey {
    public static final int LARGE_TILE_CUTOFF_CHUNKS = 512;
    public static final int COVERAGE_GRID_SIZE = 64;
    public static final int COVERAGE_CELL_COUNT = COVERAGE_GRID_SIZE * COVERAGE_GRID_SIZE;

    private GlobeAtlasSurvey() {
    }

    public static boolean surveyMode(final DimensionTiling tiling) {
        return tiling.tileSizeChunks() > LARGE_TILE_CUTOFF_CHUNKS;
    }

    public static boolean surveyMode(final int tileSizeBlocks) {
        return tileSizeBlocks > LARGE_TILE_CUTOFF_CHUNKS * 16;
    }
}
