package globe.world.config;

import globe.world.util.DimensionTiling;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public class GlobeConfig {
    public static final int DEFAULT_TILE_SIZE_CHUNKS = 1024;

    private static volatile TilingSettings settings = TilingSettings.DEFAULT;
    private static volatile GlobeSettings globeSettings = GlobeSettings.DEFAULT;
    private static volatile int settingsVersion = 0;

    public static void setTilingSettings(TilingSettings newSettings) {
        TilingSettings sanitized = newSettings.sanitized();
        if (!sanitized.equals(settings)) {
            settings = sanitized;
            globeSettings = GlobeSettings.from(sanitized);
            settingsVersion++;
        }
    }

    public static TilingSettings tilingSettings() {
        return settings;
    }

    public static GlobeSettings globeSettings() {
        return globeSettings;
    }

    public static TopologySettings topologySettings() {
        return globeSettings.topology();
    }

    public static PresentationSettings presentationSettings() {
        return globeSettings.presentation();
    }

    public static GameplaySettings gameplaySettings() {
        return globeSettings.gameplay();
    }

    public static boolean enabled() {
        return settings.enabled();
    }

    public static boolean enabled(ResourceKey<Level> dimension) {
        return DimensionTiling.forDimension(dimension).enabled();
    }

    public static int settingsVersion() {
        return settingsVersion;
    }

    public static int tileSizeChunks() {
        return settings.tileSize();
    }

    public static int tileSizeBlocks() {
        return tileSizeChunks() * 16;
    }

    public static int tileSizeChunks(ResourceKey<Level> dimension) {
        return DimensionTiling.forDimension(dimension).tileSizeChunks();
    }

    public static int tileSizeBlocks(ResourceKey<Level> dimension) {
        return DimensionTiling.forDimension(dimension).tileSizeBlocks();
    }

    public static int curvaturePercent() {
        return settings.curvaturePercent();
    }

    public static int curvaturePercent(ResourceKey<Level> dimension) {
        if (Level.NETHER.equals(dimension)) {
            return settings.netherCurvaturePercent();
        }
        return settings.curvaturePercent();
    }

    public static int netherCurvaturePercent() {
        return settings.netherCurvaturePercent();
    }

    public static boolean netherEnabled() {
        return settings.netherEnabled();
    }

    public static int netherTileSizeChunks() {
        return settings.netherTileSize();
    }

    public static int netherPortalScaleNumerator() {
        return settings.netherPortalScaleNumerator();
    }

    public static int netherPortalScaleDenominator() {
        return settings.netherPortalScaleDenominator();
    }

    public static String netherPortalScaleLabel() {
        return settings.netherPortalScaleLabel();
    }

    public static double netherPortalTeleportationScale(ServerLevel from, ServerLevel to) {
        if (Level.OVERWORLD.equals(from.dimension()) && Level.NETHER.equals(to.dimension())) {
            return (double) settings.netherPortalScaleDenominator() / (double) settings.netherPortalScaleNumerator();
        }
        if (Level.NETHER.equals(from.dimension()) && Level.OVERWORLD.equals(to.dimension())) {
            return (double) settings.netherPortalScaleNumerator() / (double) settings.netherPortalScaleDenominator();
        }
        return DimensionType.getTeleportationScale(from.dimensionType(), to.dimensionType());
    }

    public static DayNightCycleMode dayNightCycleMode() {
        return settings.dayNightCycleMode();
    }

    public static double dayLengthMultiplier() {
        return settings.dayLengthMultiplier();
    }

    public static boolean forceMissingStronghold() {
        return settings.forceMissingStronghold();
    }

    public static boolean forceMissingNetherFortress() {
        return settings.forceMissingNetherFortress();
    }
}
