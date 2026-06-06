package globe.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import globe.world.util.TerrainMode;

public record TilingSettings(
        TilingMode mode,
        int tileSize,
        TerrainMode terrainMode,
        int curvaturePercent,
        int netherCurvaturePercent,
        TilingMode netherMode,
        TerrainMode netherTerrainMode,
        boolean netherOneEighthOverworldSize,
        DayNightCycleMode dayNightCycleMode,
        double dayLengthMultiplier
) {
    public static final int CURVATURE_DISABLED_PERCENT = 0;
    public static final int CURVATURE_COMFORTABLE_PERCENT = 50;
    public static final int CURVATURE_REALISTIC_PERCENT = 100;
    public static final float CURVATURE_REALISTIC_SCALE = 12.0F;
    public static final double DAY_LENGTH_DEFAULT_MULTIPLIER = 1.0D;
    public static final double DAY_LENGTH_HALF_MULTIPLIER = 0.5D;
    public static final double DAY_LENGTH_MAX_MULTIPLIER = 10.0D;
    public static final TilingSettings DISABLED = new TilingSettings(
            TilingMode.DISABLED,
            GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS,
            TerrainMode.AUTO,
            CURVATURE_DISABLED_PERCENT,
            CURVATURE_DISABLED_PERCENT,
            TilingMode.DISABLED,
            TerrainMode.AUTO,
            true,
            DayNightCycleMode.VANILLA,
            DAY_LENGTH_DEFAULT_MULTIPLIER
    );
    public static final TilingSettings DEFAULT = new TilingSettings(
            TilingMode.DISABLED,
            GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS,
            TerrainMode.AUTO,
            CURVATURE_COMFORTABLE_PERCENT,
            CURVATURE_DISABLED_PERCENT,
            TilingMode.DISABLED,
            TerrainMode.AUTO,
            true,
            DayNightCycleMode.VANILLA,
            DAY_LENGTH_DEFAULT_MULTIPLIER
    );
    public static final Codec<TilingSettings> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            TilingMode.CODEC.optionalFieldOf("mode", TilingMode.SQUARE)
                                    .forGetter(TilingSettings::mode),
                            Codec.INT.optionalFieldOf("tile_size", GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS)
                                    .forGetter(TilingSettings::tileSize),
                            TerrainMode.CODEC.optionalFieldOf("terrain_mode", TerrainMode.AUTO)
                                    .forGetter(TilingSettings::terrainMode),
                            Codec.INT.optionalFieldOf("curvature_percent", CURVATURE_COMFORTABLE_PERCENT)
                                    .forGetter(TilingSettings::curvaturePercent),
                            Codec.INT.optionalFieldOf("nether_curvature_percent", CURVATURE_COMFORTABLE_PERCENT)
                                    .forGetter(TilingSettings::netherCurvaturePercent),
                            TilingMode.CODEC.optionalFieldOf("nether_mode", TilingMode.DISABLED)
                                    .forGetter(TilingSettings::netherMode),
                            TerrainMode.CODEC.optionalFieldOf("nether_terrain_mode", TerrainMode.AUTO)
                                    .forGetter(TilingSettings::netherTerrainMode),
                            Codec.BOOL.optionalFieldOf("nether_one_eighth_overworld_size", true)
                                    .forGetter(TilingSettings::netherOneEighthOverworldSize),
                            DayNightCycleMode.CODEC.optionalFieldOf("day_night_cycle", DayNightCycleMode.VANILLA)
                                    .forGetter(TilingSettings::dayNightCycleMode),
                            Codec.DOUBLE.optionalFieldOf("day_length_multiplier", DAY_LENGTH_DEFAULT_MULTIPLIER)
                                    .forGetter(TilingSettings::dayLengthMultiplier)
                    ).apply(instance, TilingSettings::new)
            );

    public static TilingSettings square(int tileSize) {
        return new TilingSettings(
                TilingMode.SQUARE,
                tileSize,
                TerrainMode.AUTO,
                CURVATURE_COMFORTABLE_PERCENT,
                CURVATURE_COMFORTABLE_PERCENT,
                TilingMode.DISABLED,
                TerrainMode.AUTO,
                true,
                DayNightCycleMode.VANILLA,
                DAY_LENGTH_DEFAULT_MULTIPLIER
        ).sanitized();
    }

    public boolean enabled() {
        return mode == TilingMode.SQUARE;
    }

    public boolean netherEnabled() {
        return netherMode == TilingMode.SQUARE;
    }

    public boolean supportsNetherOneEighthOverworldSize() {
        return tileSize >= 16 && tileSize % 8 == 0;
    }

    public boolean effectiveNetherOneEighthOverworldSize() {
        return netherOneEighthOverworldSize && supportsNetherOneEighthOverworldSize();
    }

    public int netherTileSize() {
        return effectiveNetherOneEighthOverworldSize()
                ? Math.max(1, tileSize / 8)
                : tileSize;
    }

    public TilingSettings withMode(TilingMode newMode) {
        return new TilingSettings(
                newMode,
                tileSize,
                terrainMode,
                curvaturePercent,
                netherCurvaturePercent,
                netherMode,
                netherTerrainMode,
                netherOneEighthOverworldSize,
                dayNightCycleMode,
                dayLengthMultiplier
        ).sanitized();
    }

    public TilingSettings withTileSize(int newTileSize) {
        return new TilingSettings(
                mode,
                newTileSize,
                TerrainMode.AUTO,
                curvaturePercent,
                netherCurvaturePercent,
                netherMode,
                TerrainMode.AUTO,
                netherOneEighthOverworldSize,
                dayNightCycleMode,
                dayLengthMultiplier
        ).sanitized();
    }

    public TilingSettings withTerrainMode(TerrainMode newTerrainMode) {
        return new TilingSettings(
                mode,
                tileSize,
                newTerrainMode,
                curvaturePercent,
                netherCurvaturePercent,
                netherMode,
                netherTerrainMode,
                netherOneEighthOverworldSize,
                dayNightCycleMode,
                dayLengthMultiplier
        ).sanitized();
    }

    public TilingSettings withCurvaturePercent(int newCurvaturePercent) {
        return new TilingSettings(
                mode,
                tileSize,
                terrainMode,
                newCurvaturePercent,
                netherCurvaturePercent,
                netherMode,
                netherTerrainMode,
                netherOneEighthOverworldSize,
                dayNightCycleMode,
                dayLengthMultiplier
        ).sanitized();
    }

    public TilingSettings withNetherCurvaturePercent(int newNetherCurvaturePercent) {
        return new TilingSettings(
                mode,
                tileSize,
                terrainMode,
                curvaturePercent,
                newNetherCurvaturePercent,
                netherMode,
                netherTerrainMode,
                netherOneEighthOverworldSize,
                dayNightCycleMode,
                dayLengthMultiplier
        ).sanitized();
    }

    public TilingSettings withNetherMode(TilingMode newNetherMode) {
        return new TilingSettings(
                mode,
                tileSize,
                terrainMode,
                curvaturePercent,
                netherCurvaturePercent,
                newNetherMode,
                TerrainMode.AUTO,
                netherOneEighthOverworldSize,
                dayNightCycleMode,
                dayLengthMultiplier
        ).sanitized();
    }

    public TilingSettings withNetherTerrainMode(TerrainMode newNetherTerrainMode) {
        return new TilingSettings(
                mode,
                tileSize,
                terrainMode,
                curvaturePercent,
                netherCurvaturePercent,
                netherMode,
                newNetherTerrainMode,
                netherOneEighthOverworldSize,
                dayNightCycleMode,
                dayLengthMultiplier
        ).sanitized();
    }

    public TilingSettings withNetherOneEighthOverworldSize(boolean newNetherOneEighthOverworldSize) {
        return new TilingSettings(
                mode,
                tileSize,
                terrainMode,
                curvaturePercent,
                netherCurvaturePercent,
                netherMode,
                TerrainMode.AUTO,
                newNetherOneEighthOverworldSize,
                dayNightCycleMode,
                dayLengthMultiplier
        ).sanitized();
    }

    public TilingSettings withDayNightCycleMode(DayNightCycleMode newDayNightCycleMode) {
        return new TilingSettings(
                mode,
                tileSize,
                terrainMode,
                curvaturePercent,
                netherCurvaturePercent,
                netherMode,
                netherTerrainMode,
                netherOneEighthOverworldSize,
                newDayNightCycleMode,
                dayLengthMultiplier
        ).sanitized();
    }

    public TilingSettings withDayLengthMultiplier(double newDayLengthMultiplier) {
        return new TilingSettings(
                mode,
                tileSize,
                terrainMode,
                curvaturePercent,
                netherCurvaturePercent,
                netherMode,
                netherTerrainMode,
                netherOneEighthOverworldSize,
                dayNightCycleMode,
                newDayLengthMultiplier
        ).sanitized();
    }

    public TilingSettings sanitized() {
        return new TilingSettings(
                mode != null ? mode : TilingMode.DISABLED,
                Math.max(1, tileSize),
                sanitizeTerrainMode(mode, terrainMode),
                sanitizeCurvaturePercent(curvaturePercent),
                sanitizeCurvaturePercent(netherCurvaturePercent),
                netherMode != null ? netherMode : TilingMode.DISABLED,
                sanitizeTerrainMode(netherMode, netherTerrainMode),
                netherOneEighthOverworldSize && Math.max(1, tileSize) >= 16 && Math.max(1, tileSize) % 8 == 0,
                dayNightCycleMode != null ? dayNightCycleMode : DayNightCycleMode.VANILLA,
                sanitizeDayLengthMultiplier(dayLengthMultiplier)
        );
    }

    private static TerrainMode sanitizeTerrainMode(TilingMode tilingMode, TerrainMode terrainMode) {
        if (tilingMode != TilingMode.SQUARE || terrainMode == null || terrainMode == TerrainMode.DISABLED) {
            return TerrainMode.AUTO;
        }
        return terrainMode;
    }

    public static int sanitizeCurvaturePercent(int percent) {
        return Math.clamp(percent, CURVATURE_DISABLED_PERCENT, CURVATURE_REALISTIC_PERCENT);
    }

    public static float curvatureScaleFromPercent(int percent) {
        return sanitizeCurvaturePercent(percent) * CURVATURE_REALISTIC_SCALE / 100.0F;
    }

    public static double sanitizeDayLengthMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier)) {
            return DAY_LENGTH_DEFAULT_MULTIPLIER;
        }
        if (multiplier <= 0.75D) {
            return DAY_LENGTH_HALF_MULTIPLIER;
        }
        return Math.clamp(Math.rint(multiplier), DAY_LENGTH_DEFAULT_MULTIPLIER, DAY_LENGTH_MAX_MULTIPLIER);
    }
}
