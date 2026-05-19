package globe.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TilingSettings(TilingMode mode, int tileSize) {
    public static final TilingSettings DISABLED = new TilingSettings(TilingMode.DISABLED, GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS);
    public static final TilingSettings DEFAULT = new TilingSettings(TilingMode.SQUARE, GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS);
    public static final Codec<TilingSettings> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            TilingMode.CODEC.optionalFieldOf("mode", TilingMode.SQUARE)
                                    .forGetter(TilingSettings::mode),
                            Codec.INT.optionalFieldOf("tile_size", GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS)
                                    .forGetter(TilingSettings::tileSize)
                    ).apply(instance, TilingSettings::new)
            );

    public static TilingSettings square(int tileSize) {
        return new TilingSettings(TilingMode.SQUARE, tileSize).sanitized();
    }

    public boolean enabled() {
        return mode == TilingMode.SQUARE;
    }

    public TilingSettings withMode(TilingMode newMode) {
        return new TilingSettings(newMode, tileSize).sanitized();
    }

    public TilingSettings withTileSize(int newTileSize) {
        return new TilingSettings(mode, newTileSize).sanitized();
    }

    public TilingSettings sanitized() {
        return new TilingSettings(mode, Math.max(1, tileSize));
    }
}
