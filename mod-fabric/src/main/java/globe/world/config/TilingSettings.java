package globe.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import globe.world.util.TerrainMode;

import java.util.List;
import java.util.Optional;

public record TilingSettings(
        TilingMode mode,
        int tileSize,
        TerrainMode terrainMode,
        int curvaturePercent,
        int netherCurvaturePercent,
        TilingMode netherMode,
        TerrainMode netherTerrainMode,
        int netherTileSize,
        int netherPortalScaleNumerator,
        int netherPortalScaleDenominator,
        DayNightCycleMode dayNightCycleMode,
        double dayLengthMultiplier,
        boolean forceMissingStronghold,
        boolean forceMissingNetherFortress
) {
    public static final int CURVATURE_DISABLED_PERCENT = 0;
    public static final int CURVATURE_COMFORTABLE_PERCENT = 50;
    public static final int CURVATURE_REALISTIC_PERCENT = 100;
    public static final float CURVATURE_REALISTIC_SCALE = 12.0F;
    public static final double DAY_LENGTH_DEFAULT_MULTIPLIER = 1.0D;
    public static final double DAY_LENGTH_HALF_MULTIPLIER = 0.5D;
    public static final double DAY_LENGTH_MAX_MULTIPLIER = 10.0D;
    public static final int FORCED_STRUCTURE_SMALL_TILE_MAX_CHUNKS = 256;
    public static final int DEFAULT_NETHER_TILE_SIZE_CHUNKS = Math.max(1, GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS / 8);
    public static final int DEFAULT_NETHER_PORTAL_SCALE_NUMERATOR = 8;
    public static final int DEFAULT_NETHER_PORTAL_SCALE_DENOMINATOR = 1;

    private static final PortalScale DEFAULT_NETHER_PORTAL_SCALE = new PortalScale(
            DEFAULT_NETHER_PORTAL_SCALE_NUMERATOR,
            DEFAULT_NETHER_PORTAL_SCALE_DENOMINATOR
    );
    private static final List<PortalScale> ALLOWED_NETHER_PORTAL_SCALES = List.of(
            new PortalScale(1, 32),
            new PortalScale(1, 16),
            new PortalScale(1, 8),
            new PortalScale(1, 4),
            new PortalScale(1, 2),
            new PortalScale(1, 1),
            new PortalScale(2, 1),
            new PortalScale(4, 1),
            DEFAULT_NETHER_PORTAL_SCALE,
            new PortalScale(16, 1),
            new PortalScale(32, 1)
    );

    public static final TilingSettings DISABLED = new TilingSettings(
            TilingMode.DISABLED,
            GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS,
            TerrainMode.AUTO,
            CURVATURE_DISABLED_PERCENT,
            CURVATURE_DISABLED_PERCENT,
            TilingMode.DISABLED,
            TerrainMode.AUTO,
            DEFAULT_NETHER_TILE_SIZE_CHUNKS,
            DEFAULT_NETHER_PORTAL_SCALE_NUMERATOR,
            DEFAULT_NETHER_PORTAL_SCALE_DENOMINATOR,
            DayNightCycleMode.VANILLA,
            DAY_LENGTH_DEFAULT_MULTIPLIER,
            false,
            false
    );
    public static final TilingSettings DEFAULT = new TilingSettings(
            TilingMode.DISABLED,
            GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS,
            TerrainMode.AUTO,
            CURVATURE_COMFORTABLE_PERCENT,
            CURVATURE_DISABLED_PERCENT,
            TilingMode.DISABLED,
            TerrainMode.AUTO,
            DEFAULT_NETHER_TILE_SIZE_CHUNKS,
            DEFAULT_NETHER_PORTAL_SCALE_NUMERATOR,
            DEFAULT_NETHER_PORTAL_SCALE_DENOMINATOR,
            DayNightCycleMode.VANILLA,
            DAY_LENGTH_DEFAULT_MULTIPLIER,
            false,
            false
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
                            Codec.INT.optionalFieldOf("nether_tile_size", DEFAULT_NETHER_TILE_SIZE_CHUNKS)
                                    .forGetter(TilingSettings::netherTileSize),
                            Codec.INT.optionalFieldOf("nether_portal_scale_numerator", DEFAULT_NETHER_PORTAL_SCALE_NUMERATOR)
                                    .forGetter(TilingSettings::netherPortalScaleNumerator),
                            Codec.INT.optionalFieldOf("nether_portal_scale_denominator", DEFAULT_NETHER_PORTAL_SCALE_DENOMINATOR)
                                    .forGetter(TilingSettings::netherPortalScaleDenominator),
                            DayNightCycleMode.CODEC.optionalFieldOf("day_night_cycle", DayNightCycleMode.VANILLA)
                                    .forGetter(TilingSettings::dayNightCycleMode),
                            Codec.DOUBLE.optionalFieldOf("day_length_multiplier", DAY_LENGTH_DEFAULT_MULTIPLIER)
                                    .forGetter(TilingSettings::dayLengthMultiplier),
                            Codec.BOOL.optionalFieldOf("force_missing_stronghold")
                                    .forGetter(settings -> Optional.of(settings.forceMissingStronghold())),
                            Codec.BOOL.optionalFieldOf("force_missing_nether_fortress")
                                    .forGetter(settings -> Optional.of(settings.forceMissingNetherFortress()))
                    ).apply(instance, TilingSettings::create)
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
                defaultNetherTileSize(tileSize),
                DEFAULT_NETHER_PORTAL_SCALE_NUMERATOR,
                DEFAULT_NETHER_PORTAL_SCALE_DENOMINATOR,
                DayNightCycleMode.VANILLA,
                DAY_LENGTH_DEFAULT_MULTIPLIER,
                defaultForceMissingStronghold(TilingMode.SQUARE, tileSize),
                false
        ).sanitized();
    }

    public boolean enabled() {
        return mode == TilingMode.SQUARE;
    }

    public boolean netherEnabled() {
        return netherMode == TilingMode.SQUARE;
    }

    public int netherTileSize() {
        return sanitizeTileSize(netherTileSize);
    }

    public int netherPortalScaleNumerator() {
        return sanitizeNetherPortalScale(netherPortalScaleNumerator, netherPortalScaleDenominator).numerator();
    }

    public int netherPortalScaleDenominator() {
        return sanitizeNetherPortalScale(netherPortalScaleNumerator, netherPortalScaleDenominator).denominator();
    }

    public String netherPortalScaleLabel() {
        PortalScale scale = sanitizeNetherPortalScale(netherPortalScaleNumerator, netherPortalScaleDenominator);
        if (scale.numerator() == 1 && scale.denominator() == 1) {
            return "1:1";
        }
        if (scale.numerator() == 1) {
            return "1:%d reverse".formatted(scale.denominator());
        }
        if (scale.numerator() == 8) {
            return "1:8 vanilla";
        }
        return "1:%d".formatted(scale.numerator());
    }

    public String netherPortalScaleSummaryLabel() {
        PortalScale scale = sanitizeNetherPortalScale(netherPortalScaleNumerator, netherPortalScaleDenominator);
        if (scale.numerator() == 1 && scale.denominator() == 1) {
            return "1:1";
        }
        if (scale.numerator() == 1) {
            return "1:%d reverse".formatted(scale.denominator());
        }
        return "1:%d".formatted(scale.numerator());
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
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                dayNightCycleMode,
                dayLengthMultiplier,
                defaultForceMissingStronghold(newMode, tileSize),
                forceMissingNetherFortress
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
                netherTerrainMode,
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                dayNightCycleMode,
                dayLengthMultiplier,
                defaultForceMissingStronghold(mode, newTileSize),
                forceMissingNetherFortress
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
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                dayNightCycleMode,
                dayLengthMultiplier,
                forceMissingStronghold,
                forceMissingNetherFortress
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
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                dayNightCycleMode,
                dayLengthMultiplier,
                forceMissingStronghold,
                forceMissingNetherFortress
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
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                dayNightCycleMode,
                dayLengthMultiplier,
                forceMissingStronghold,
                forceMissingNetherFortress
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
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                dayNightCycleMode,
                dayLengthMultiplier,
                forceMissingStronghold,
                defaultForceMissingNetherStructure(newNetherMode, netherTileSize)
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
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                dayNightCycleMode,
                dayLengthMultiplier,
                forceMissingStronghold,
                forceMissingNetherFortress
        ).sanitized();
    }

    public TilingSettings withNetherTileSize(int newNetherTileSize) {
        int sanitizedNetherTileSize = sanitizeTileSize(newNetherTileSize);
        return new TilingSettings(
                mode,
                tileSize,
                terrainMode,
                curvaturePercent,
                netherCurvaturePercent,
                netherMode,
                TerrainMode.AUTO,
                sanitizedNetherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                dayNightCycleMode,
                dayLengthMultiplier,
                forceMissingStronghold,
                defaultForceMissingNetherStructure(netherMode, sanitizedNetherTileSize)
        ).sanitized();
    }

    public TilingSettings withNetherPortalScale(int numerator, int denominator) {
        PortalScale scale = sanitizeNetherPortalScale(numerator, denominator);
        return new TilingSettings(
                mode,
                tileSize,
                terrainMode,
                curvaturePercent,
                netherCurvaturePercent,
                netherMode,
                netherTerrainMode,
                netherTileSize,
                scale.numerator(),
                scale.denominator(),
                dayNightCycleMode,
                dayLengthMultiplier,
                forceMissingStronghold,
                forceMissingNetherFortress
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
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                newDayNightCycleMode,
                dayLengthMultiplier,
                forceMissingStronghold,
                forceMissingNetherFortress
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
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                dayNightCycleMode,
                newDayLengthMultiplier,
                forceMissingStronghold,
                forceMissingNetherFortress
        ).sanitized();
    }

    public TilingSettings withForceMissingStronghold(boolean newForceMissingStronghold) {
        return new TilingSettings(
                mode,
                tileSize,
                terrainMode,
                curvaturePercent,
                netherCurvaturePercent,
                netherMode,
                netherTerrainMode,
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                dayNightCycleMode,
                dayLengthMultiplier,
                newForceMissingStronghold,
                forceMissingNetherFortress
        ).sanitized();
    }

    public TilingSettings withForceMissingNetherFortress(boolean newForceMissingNetherFortress) {
        return new TilingSettings(
                mode,
                tileSize,
                terrainMode,
                curvaturePercent,
                netherCurvaturePercent,
                netherMode,
                netherTerrainMode,
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                dayNightCycleMode,
                dayLengthMultiplier,
                forceMissingStronghold,
                newForceMissingNetherFortress
        ).sanitized();
    }

    public TilingSettings sanitized() {
        int sanitizedTileSize = sanitizeTileSize(tileSize);
        int sanitizedNetherTileSize = sanitizeTileSize(netherTileSize);
        TilingMode sanitizedMode = mode != null ? mode : TilingMode.DISABLED;
        TilingMode sanitizedNetherMode = netherMode != null ? netherMode : TilingMode.DISABLED;
        PortalScale scale = sanitizeNetherPortalScale(netherPortalScaleNumerator, netherPortalScaleDenominator);
        return new TilingSettings(
                sanitizedMode,
                sanitizedTileSize,
                sanitizeTerrainMode(sanitizedMode, terrainMode),
                sanitizeCurvaturePercent(curvaturePercent),
                sanitizeCurvaturePercent(netherCurvaturePercent),
                sanitizedNetherMode,
                sanitizeTerrainMode(sanitizedNetherMode, netherTerrainMode),
                sanitizedNetherTileSize,
                scale.numerator(),
                scale.denominator(),
                dayNightCycleMode != null ? dayNightCycleMode : DayNightCycleMode.VANILLA,
                sanitizeDayLengthMultiplier(dayLengthMultiplier),
                forceMissingStronghold,
                forceMissingNetherFortress
        );
    }

    private static TilingSettings create(
            TilingMode mode,
            int tileSize,
            TerrainMode terrainMode,
            int curvaturePercent,
            int netherCurvaturePercent,
            TilingMode netherMode,
            TerrainMode netherTerrainMode,
            int netherTileSize,
            int netherPortalScaleNumerator,
            int netherPortalScaleDenominator,
            DayNightCycleMode dayNightCycleMode,
            double dayLengthMultiplier,
            Optional<Boolean> forceMissingStronghold,
            Optional<Boolean> forceMissingNetherFortress) {
        int sanitizedNetherTileSize = sanitizeTileSize(netherTileSize);
        PortalScale scale = sanitizeNetherPortalScale(netherPortalScaleNumerator, netherPortalScaleDenominator);
        return new TilingSettings(
                mode,
                tileSize,
                terrainMode,
                curvaturePercent,
                netherCurvaturePercent,
                netherMode,
                netherTerrainMode,
                sanitizedNetherTileSize,
                scale.numerator(),
                scale.denominator(),
                dayNightCycleMode,
                dayLengthMultiplier,
                forceMissingStronghold.orElseGet(() -> defaultForceMissingStronghold(mode, tileSize)),
                forceMissingNetherFortress.orElseGet(() -> defaultForceMissingNetherStructure(netherMode, sanitizedNetherTileSize))
        ).sanitized();
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

    public static int defaultNetherTileSize(int overworldTileSize) {
        int sanitizedTileSize = sanitizeTileSize(overworldTileSize);
        return sanitizedTileSize >= 8 && sanitizedTileSize % 8 == 0
                ? Math.max(1, sanitizedTileSize / 8)
                : sanitizedTileSize;
    }

    private static int sanitizeTileSize(int tileSize) {
        return Math.max(1, tileSize);
    }

    private static PortalScale sanitizeNetherPortalScale(int numerator, int denominator) {
        PortalScale requested = new PortalScale(Math.max(1, numerator), Math.max(1, denominator));
        return ALLOWED_NETHER_PORTAL_SCALES.contains(requested) ? requested : DEFAULT_NETHER_PORTAL_SCALE;
    }

    private static boolean defaultForceMissingStronghold(TilingMode mode, int tileSize) {
        return mode == TilingMode.SQUARE && isSmallProgressionTile(tileSize);
    }

    private static boolean defaultForceMissingNetherStructure(TilingMode mode, int tileSize) {
        return mode == TilingMode.SQUARE && isSmallProgressionTile(tileSize);
    }

    private static boolean isSmallProgressionTile(int tileSize) {
        return sanitizeTileSize(tileSize) <= FORCED_STRUCTURE_SMALL_TILE_MAX_CHUNKS;
    }

    private record PortalScale(int numerator, int denominator) {
    }
}
