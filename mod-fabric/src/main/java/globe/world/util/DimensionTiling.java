package globe.world.util;

import globe.world.config.GlobeConfig;
import globe.world.config.TilingMode;
import globe.world.config.TopologySettings;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

public record DimensionTiling(TilingMode mode, boolean enabled, int tileSizeChunks, TerrainMode terrainMode) {
    public static final DimensionTiling DISABLED = new DimensionTiling(TilingMode.DISABLED, false, 1, TerrainMode.DISABLED);

    private static final ThreadLocal<DimensionTiling> CURRENT = new ThreadLocal<>();

    public DimensionTiling {
        mode = mode == null ? TilingMode.DISABLED : mode;
        enabled = enabled && (mode == TilingMode.SQUARE || mode == TilingMode.HEX);
        tileSizeChunks = enabled
                ? TopologySettings.sanitizeTileSize(mode, tileSizeChunks)
                : Math.max(1, tileSizeChunks);
        if (!enabled) {
            mode = TilingMode.DISABLED;
            terrainMode = TerrainMode.DISABLED;
        } else if (mode == TilingMode.HEX) {
            terrainMode = TerrainMode.EDGE_BLEND;
        } else if (terrainMode == null || terrainMode == TerrainMode.AUTO || terrainMode == TerrainMode.DISABLED) {
            terrainMode = TerrainMode.forOverworldTileSize(tileSizeChunks);
        }
    }

    public DimensionTiling(boolean enabled, int tileSizeChunks, TerrainMode terrainMode) {
        this(enabled ? TilingMode.SQUARE : TilingMode.DISABLED, enabled, tileSizeChunks, terrainMode);
    }

    public DimensionTiling(boolean enabled, int tileSizeChunks) {
        this(enabled, tileSizeChunks, enabled ? TerrainMode.forOverworldTileSize(tileSizeChunks) : TerrainMode.DISABLED);
    }

    public static DimensionTiling forDimension(ResourceKey<Level> dimension) {
        TopologySettings settings = GlobeConfig.topologySettings();
        if (Level.OVERWORLD.equals(dimension)) {
            return settings.enabled()
                    ? new DimensionTiling(
                            settings.mode(),
                            true,
                            settings.tileSize(),
                            resolveTerrainMode(settings.mode(), settings.terrainMode(), TerrainMode.forOverworldTileSize(settings.tileSize()))
                    )
                    : DISABLED;
        }
        if (Level.NETHER.equals(dimension)) {
            return settings.netherEnabled()
                    ? new DimensionTiling(
                            settings.netherMode(),
                            true,
                            settings.netherTileSize(),
                            resolveTerrainMode(
                                    settings.netherMode(),
                                    settings.netherTerrainMode(),
                                    TerrainMode.forNetherTileSize(settings.netherTileSize())
                            )
                    )
                    : DISABLED;
        }
        return DISABLED;
    }

    private static TerrainMode resolveTerrainMode(TilingMode mode, TerrainMode configured, TerrainMode fallback) {
        if (mode == TilingMode.HEX) {
            return TerrainMode.EDGE_BLEND;
        }
        return configured == null || configured == TerrainMode.AUTO || configured == TerrainMode.DISABLED
                ? fallback
                : configured;
    }

    public static DimensionTiling forLevel(Level level) {
        return forDimension(level.dimension());
    }

    public static DimensionTiling currentOrOverworld() {
        DimensionTiling current = CURRENT.get();
        return current != null ? current : forDimension(Level.OVERWORLD);
    }

    private static void push(DimensionTiling tiling) {
        CURRENT.set(tiling);
    }

    private static void clear() {
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

    public static void runWith(DimensionTiling tiling, Runnable action) {
        DimensionTiling previous = CURRENT.get();
        push(tiling);
        try {
            action.run();
        } finally {
            if (previous == null) {
                clear();
            } else {
                push(previous);
            }
        }
    }

    public int tileSizeBlocks() {
        return tileSizeChunks * 16;
    }
}
