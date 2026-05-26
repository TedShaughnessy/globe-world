package globe.world.util;

import globe.world.config.GlobeConfig;
import globe.world.config.TilingSettings;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

public record DimensionTiling(boolean enabled, int tileSizeChunks, TerrainMode terrainMode) {
    public static final DimensionTiling DISABLED = new DimensionTiling(false, 1, TerrainMode.DISABLED);

    private static final ThreadLocal<DimensionTiling> CURRENT = new ThreadLocal<>();

    public DimensionTiling {
        tileSizeChunks = Math.max(1, tileSizeChunks);
        if (!enabled) {
            terrainMode = TerrainMode.DISABLED;
        } else if (terrainMode == null || terrainMode == TerrainMode.DISABLED) {
            terrainMode = TerrainMode.forOverworldTileSize(tileSizeChunks);
        }
    }

    public DimensionTiling(boolean enabled, int tileSizeChunks) {
        this(enabled, tileSizeChunks, enabled ? TerrainMode.forOverworldTileSize(tileSizeChunks) : TerrainMode.DISABLED);
    }

    public static DimensionTiling forDimension(ResourceKey<Level> dimension) {
        TilingSettings settings = GlobeConfig.tilingSettings();
        if (Level.OVERWORLD.equals(dimension)) {
            return settings.enabled()
                    ? new DimensionTiling(true, settings.tileSize(), TerrainMode.forOverworldTileSize(settings.tileSize()))
                    : DISABLED;
        }
        if (Level.NETHER.equals(dimension)) {
            return settings.netherEnabled()
                    ? new DimensionTiling(true, settings.netherTileSize(), TerrainMode.forNetherTileSize(settings.netherTileSize()))
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

    public static <T> T with(DimensionTiling tiling, Supplier<T> action) {
        DimensionTiling previous = CURRENT.get();
        CURRENT.set(tiling);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public int tileSizeBlocks() {
        return tileSizeChunks * 16;
    }
}
