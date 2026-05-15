package globe.world.util;

import globe.world.config.GlobeConfig;

public class CoordUtil {
    public static int wrapChunk(int c) {
        int half = GlobeConfig.W_CHUNKS / 2;
        return Math.floorMod(c + half, GlobeConfig.W_CHUNKS) - half;
    }

    public static int wrapBlock(int b) {
        int half = GlobeConfig.W_BLOCKS / 2;
        return Math.floorMod(b + half, GlobeConfig.W_BLOCKS) - half;
    }

    /** Returns the virtual tile of canonical coord nearest to playerCoord. */
    public static int virtualChunk(int canonical, int playerChunk) {
        int k = Math.floorDiv(playerChunk - canonical + GlobeConfig.W_CHUNKS / 2, GlobeConfig.W_CHUNKS);
        return canonical + k * GlobeConfig.W_CHUNKS;
    }
}
