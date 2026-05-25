package globe.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TilingSettings(TilingMode mode, int tileSize, int curvaturePercent) {
    public static final int CURVATURE_DISABLED_PERCENT = 0;
    public static final int CURVATURE_COMFORTABLE_PERCENT = 50;
    public static final int CURVATURE_REALISTIC_PERCENT = 100;
    public static final float CURVATURE_REALISTIC_SCALE = 12.0F;
    public static final TilingSettings DISABLED = new TilingSettings(
            TilingMode.DISABLED,
            GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS,
            CURVATURE_DISABLED_PERCENT
    );
    public static final TilingSettings DEFAULT = new TilingSettings(
            TilingMode.DISABLED,
            GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS,
            CURVATURE_COMFORTABLE_PERCENT
    );
    public static final Codec<TilingSettings> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            TilingMode.CODEC.optionalFieldOf("mode", TilingMode.SQUARE)
                                    .forGetter(TilingSettings::mode),
                            Codec.INT.optionalFieldOf("tile_size", GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS)
                                    .forGetter(TilingSettings::tileSize),
                            Codec.INT.optionalFieldOf("curvature_percent", CURVATURE_COMFORTABLE_PERCENT)
                                    .forGetter(TilingSettings::curvaturePercent)
                    ).apply(instance, TilingSettings::new)
            );

    public static TilingSettings square(int tileSize) {
        return new TilingSettings(TilingMode.SQUARE, tileSize, CURVATURE_COMFORTABLE_PERCENT).sanitized();
    }

    public boolean enabled() {
        return mode == TilingMode.SQUARE;
    }

    public TilingSettings withMode(TilingMode newMode) {
        return new TilingSettings(newMode, tileSize, curvaturePercent).sanitized();
    }

    public TilingSettings withTileSize(int newTileSize) {
        return new TilingSettings(mode, newTileSize, curvaturePercent).sanitized();
    }

    public TilingSettings withCurvaturePercent(int newCurvaturePercent) {
        return new TilingSettings(mode, tileSize, newCurvaturePercent).sanitized();
    }

    public TilingSettings sanitized() {
        return new TilingSettings(mode, Math.max(1, tileSize), sanitizeCurvaturePercent(curvaturePercent));
    }

    public static int sanitizeCurvaturePercent(int percent) {
        return Math.clamp(percent, CURVATURE_DISABLED_PERCENT, CURVATURE_REALISTIC_PERCENT);
    }

    public static float curvatureScaleFromPercent(int percent) {
        return sanitizeCurvaturePercent(percent) * CURVATURE_REALISTIC_SCALE / 100.0F;
    }
}
