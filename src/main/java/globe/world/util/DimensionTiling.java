package globe.world.util;

import globe.world.config.GlobeConfig;
import globe.world.config.TilingSettings;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record DimensionTiling(boolean enabled, int tileSizeChunks) {
    public static final DimensionTiling DISABLED = new DimensionTiling(false, 1);

    private static final ThreadLocal<DimensionTiling> CURRENT = new ThreadLocal<>();

    public DimensionTiling {
        tileSizeChunks = Math.max(1, tileSizeChunks);
    }

    public static DimensionTiling forDimension(ResourceKey<Level> dimension) {
        TilingSettings settings = GlobeConfig.tilingSettings();
        if (Level.OVERWORLD.equals(dimension)) {
            return settings.enabled()
                    ? new DimensionTiling(true, settings.tileSize())
                    : DISABLED;
        }
        if (Level.NETHER.equals(dimension)) {
            return settings.netherEnabled()
                    ? new DimensionTiling(true, settings.netherTileSize())
                    : DISABLED;
        }
        return DISABLED;
    }

    public static DimensionTiling forLevel(Level level) {
        return forDimension(level.dimension());
    }

    public static DimensionTiling currentOrOverworld() {
        DimensionTiling current = CURRENT.get();
        return current != null ? current : forDimension(Level.OVERWORLD);
    }

    public static void push(DimensionTiling tiling) {
        CURRENT.set(tiling);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public int tileSizeBlocks() {
        return tileSizeChunks * 16;
    }
}
