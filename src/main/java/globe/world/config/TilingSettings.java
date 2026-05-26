package globe.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TilingSettings(
        TilingMode mode,
        int tileSize,
        int curvaturePercent,
        TilingMode netherMode,
        boolean netherOneEighthOverworldSize,
        DayNightCycleMode dayNightCycleMode
) {
    public static final int CURVATURE_DISABLED_PERCENT = 0;
    public static final int CURVATURE_COMFORTABLE_PERCENT = 50;
    public static final int CURVATURE_REALISTIC_PERCENT = 100;
    public static final float CURVATURE_REALISTIC_SCALE = 12.0F;
    public static final TilingSettings DISABLED = new TilingSettings(
            TilingMode.DISABLED,
            GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS,
            CURVATURE_DISABLED_PERCENT,
            TilingMode.DISABLED,
            true,
            DayNightCycleMode.VANILLA
    );
    public static final TilingSettings DEFAULT = new TilingSettings(
            TilingMode.DISABLED,
            GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS,
            CURVATURE_COMFORTABLE_PERCENT,
            TilingMode.DISABLED,
            true,
            DayNightCycleMode.VANILLA
    );
    public static final Codec<TilingSettings> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            TilingMode.CODEC.optionalFieldOf("mode", TilingMode.SQUARE)
                                    .forGetter(TilingSettings::mode),
                            Codec.INT.optionalFieldOf("tile_size", GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS)
                                    .forGetter(TilingSettings::tileSize),
                            Codec.INT.optionalFieldOf("curvature_percent", CURVATURE_COMFORTABLE_PERCENT)
                                    .forGetter(TilingSettings::curvaturePercent),
                            TilingMode.CODEC.optionalFieldOf("nether_mode", TilingMode.DISABLED)
                                    .forGetter(TilingSettings::netherMode),
                            Codec.BOOL.optionalFieldOf("nether_one_eighth_overworld_size", true)
                                    .forGetter(TilingSettings::netherOneEighthOverworldSize),
                            DayNightCycleMode.CODEC.optionalFieldOf("day_night_cycle", DayNightCycleMode.VANILLA)
                                    .forGetter(TilingSettings::dayNightCycleMode)
                    ).apply(instance, TilingSettings::new)
            );

    public static TilingSettings square(int tileSize) {
        return new TilingSettings(
                TilingMode.SQUARE,
                tileSize,
                CURVATURE_COMFORTABLE_PERCENT,
                TilingMode.DISABLED,
                true,
                DayNightCycleMode.VANILLA
        ).sanitized();
    }

    public boolean enabled() {
        return mode == TilingMode.SQUARE;
    }

    public boolean netherEnabled() {
        return netherMode == TilingMode.SQUARE;
    }

    public boolean supportsNetherOneEighthOverworldSize() {
        return tileSize % 8 == 0;
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
                curvaturePercent,
                netherMode,
                netherOneEighthOverworldSize,
                dayNightCycleMode
        ).sanitized();
    }

    public TilingSettings withTileSize(int newTileSize) {
        return new TilingSettings(
                mode,
                newTileSize,
                curvaturePercent,
                netherMode,
                netherOneEighthOverworldSize,
                dayNightCycleMode
        ).sanitized();
    }

    public TilingSettings withCurvaturePercent(int newCurvaturePercent) {
        return new TilingSettings(
                mode,
                tileSize,
                newCurvaturePercent,
                netherMode,
                netherOneEighthOverworldSize,
                dayNightCycleMode
        ).sanitized();
    }

    public TilingSettings withNetherMode(TilingMode newNetherMode) {
        return new TilingSettings(
                mode,
                tileSize,
                curvaturePercent,
                newNetherMode,
                netherOneEighthOverworldSize,
                dayNightCycleMode
        ).sanitized();
    }

    public TilingSettings withNetherOneEighthOverworldSize(boolean newNetherOneEighthOverworldSize) {
        return new TilingSettings(
                mode,
                tileSize,
                curvaturePercent,
                netherMode,
                newNetherOneEighthOverworldSize,
                dayNightCycleMode
        ).sanitized();
    }

    public TilingSettings withDayNightCycleMode(DayNightCycleMode newDayNightCycleMode) {
        return new TilingSettings(
                mode,
                tileSize,
                curvaturePercent,
                netherMode,
                netherOneEighthOverworldSize,
                newDayNightCycleMode
        ).sanitized();
    }

    public TilingSettings sanitized() {
        return new TilingSettings(
                mode != null ? mode : TilingMode.DISABLED,
                Math.max(1, tileSize),
                sanitizeCurvaturePercent(curvaturePercent),
                netherMode != null ? netherMode : TilingMode.DISABLED,
                netherOneEighthOverworldSize,
                dayNightCycleMode != null ? dayNightCycleMode : DayNightCycleMode.VANILLA
        );
    }

    public static int sanitizeCurvaturePercent(int percent) {
        return Math.clamp(percent, CURVATURE_DISABLED_PERCENT, CURVATURE_REALISTIC_PERCENT);
    }

    public static float curvatureScaleFromPercent(int percent) {
        return sanitizeCurvaturePercent(percent) * CURVATURE_REALISTIC_SCALE / 100.0F;
    }
}
