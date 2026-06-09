package globe.world.config;

import globe.world.util.DimensionTiling;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public class GlobeConfig {
    public static final int DEFAULT_TILE_SIZE_CHUNKS = 1024;

    private static volatile GlobeSettings globeSettings = GlobeSettings.DEFAULT;
    private static volatile int settingsVersion = 0;

    public static void setGlobeSettings(GlobeSettings newSettings) {
        GlobeSettings sanitized = newSettings == null ? GlobeSettings.DEFAULT : newSettings;
        if (!sanitized.equals(globeSettings)) {
            globeSettings = sanitized;
            settingsVersion++;
        }
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
        return globeSettings.topology().enabled();
    }

    public static boolean enabled(ResourceKey<Level> dimension) {
        return DimensionTiling.forDimension(dimension).enabled();
    }

    public static int settingsVersion() {
        return settingsVersion;
    }

    public static int tileSizeChunks() {
        return globeSettings.topology().tileSize();
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
        return globeSettings.presentation().curvaturePercent();
    }

    public static int curvaturePercent(ResourceKey<Level> dimension) {
        if (Level.NETHER.equals(dimension)) {
            return globeSettings.presentation().netherCurvaturePercent();
        }
        return globeSettings.presentation().curvaturePercent();
    }

    public static int netherCurvaturePercent() {
        return globeSettings.presentation().netherCurvaturePercent();
    }

    public static boolean netherEnabled() {
        return globeSettings.topology().netherEnabled();
    }

    public static int netherTileSizeChunks() {
        return globeSettings.topology().netherTileSize();
    }

    public static int netherPortalScaleNumerator() {
        return globeSettings.topology().netherPortalScaleNumerator();
    }

    public static int netherPortalScaleDenominator() {
        return globeSettings.topology().netherPortalScaleDenominator();
    }

    public static String netherPortalScaleLabel() {
        return globeSettings.topology().netherPortalScaleLabel();
    }

    public static double netherPortalTeleportationScale(ServerLevel from, ServerLevel to) {
        if (Level.OVERWORLD.equals(from.dimension()) && Level.NETHER.equals(to.dimension())) {
            return (double) netherPortalScaleDenominator() / (double) netherPortalScaleNumerator();
        }
        if (Level.NETHER.equals(from.dimension()) && Level.OVERWORLD.equals(to.dimension())) {
            return (double) netherPortalScaleNumerator() / (double) netherPortalScaleDenominator();
        }
        return DimensionType.getTeleportationScale(from.dimensionType(), to.dimensionType());
    }

    public static DayNightCycleMode dayNightCycleMode() {
        return globeSettings.gameplay().dayNightCycleMode();
    }

    public static double dayLengthMultiplier() {
        return globeSettings.gameplay().dayLengthMultiplier();
    }

    public static boolean forceMissingStronghold() {
        return globeSettings.topology().forceMissingStronghold();
    }

    public static boolean forceMissingNetherFortress() {
        return globeSettings.topology().forceMissingNetherFortress();
    }
}
