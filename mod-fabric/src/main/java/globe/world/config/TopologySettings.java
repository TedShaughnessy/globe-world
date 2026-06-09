package globe.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import globe.world.util.TerrainMode;

public record TopologySettings(
        TilingMode mode,
        int tileSize,
        TerrainMode terrainMode,
        TilingMode netherMode,
        TerrainMode netherTerrainMode,
        int netherTileSize,
        int netherPortalScaleNumerator,
        int netherPortalScaleDenominator,
        boolean forceMissingStronghold,
        boolean forceMissingNetherFortress) {
    public static final TopologySettings DEFAULT = from(TilingSettings.DEFAULT);
    public static final Codec<TopologySettings> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            TilingMode.CODEC.fieldOf("mode").forGetter(TopologySettings::mode),
                            Codec.INT.fieldOf("tile_size").forGetter(TopologySettings::tileSize),
                            TerrainMode.CODEC.fieldOf("terrain_mode").forGetter(TopologySettings::terrainMode),
                            TilingMode.CODEC.fieldOf("nether_mode").forGetter(TopologySettings::netherMode),
                            TerrainMode.CODEC.fieldOf("nether_terrain_mode").forGetter(TopologySettings::netherTerrainMode),
                            Codec.INT.fieldOf("nether_tile_size").forGetter(TopologySettings::netherTileSize),
                            Codec.INT.fieldOf("nether_portal_scale_numerator").forGetter(TopologySettings::netherPortalScaleNumerator),
                            Codec.INT.fieldOf("nether_portal_scale_denominator").forGetter(TopologySettings::netherPortalScaleDenominator),
                            Codec.BOOL.fieldOf("force_missing_stronghold").forGetter(TopologySettings::forceMissingStronghold),
                            Codec.BOOL.fieldOf("force_missing_nether_fortress").forGetter(TopologySettings::forceMissingNetherFortress)
                    ).apply(instance, TopologySettings::new)
            );

    public TopologySettings {
        TilingSettings sanitized = new TilingSettings(
                mode,
                tileSize,
                terrainMode,
                PresentationSettings.DEFAULT.curvaturePercent(),
                PresentationSettings.DEFAULT.netherCurvaturePercent(),
                netherMode,
                netherTerrainMode,
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                GameplaySettings.DEFAULT.dayNightCycleMode(),
                GameplaySettings.DEFAULT.dayLengthMultiplier(),
                forceMissingStronghold,
                forceMissingNetherFortress
        ).sanitized();
        mode = sanitized.mode();
        tileSize = sanitized.tileSize();
        terrainMode = sanitized.terrainMode();
        netherMode = sanitized.netherMode();
        netherTerrainMode = sanitized.netherTerrainMode();
        netherTileSize = sanitized.netherTileSize();
        netherPortalScaleNumerator = sanitized.netherPortalScaleNumerator();
        netherPortalScaleDenominator = sanitized.netherPortalScaleDenominator();
        forceMissingStronghold = sanitized.forceMissingStronghold();
        forceMissingNetherFortress = sanitized.forceMissingNetherFortress();
    }

    public static TopologySettings from(TilingSettings settings) {
        TilingSettings sanitized = settings.sanitized();
        return new TopologySettings(
                sanitized.mode(),
                sanitized.tileSize(),
                sanitized.terrainMode(),
                sanitized.netherMode(),
                sanitized.netherTerrainMode(),
                sanitized.netherTileSize(),
                sanitized.netherPortalScaleNumerator(),
                sanitized.netherPortalScaleDenominator(),
                sanitized.forceMissingStronghold(),
                sanitized.forceMissingNetherFortress()
        );
    }

    public boolean enabled() {
        return mode == TilingMode.SQUARE;
    }

    public boolean netherEnabled() {
        return netherMode == TilingMode.SQUARE;
    }

    public String netherPortalScaleLabel() {
        if (netherPortalScaleNumerator == 1 && netherPortalScaleDenominator == 1) {
            return "1:1";
        }
        if (netherPortalScaleNumerator == 1) {
            return "1:%d reverse".formatted(netherPortalScaleDenominator);
        }
        if (netherPortalScaleNumerator == 8) {
            return "1:8 vanilla";
        }
        return "1:%d".formatted(netherPortalScaleNumerator);
    }

    public String netherPortalScaleSummaryLabel() {
        if (netherPortalScaleNumerator == 1 && netherPortalScaleDenominator == 1) {
            return "1:1";
        }
        if (netherPortalScaleNumerator == 1) {
            return "1:%d reverse".formatted(netherPortalScaleDenominator);
        }
        return "1:%d".formatted(netherPortalScaleNumerator);
    }

    public TilingSettings toTilingSettings(PresentationSettings presentation, GameplaySettings gameplay) {
        return new TilingSettings(
                mode,
                tileSize,
                terrainMode,
                presentation.curvaturePercent(),
                presentation.netherCurvaturePercent(),
                netherMode,
                netherTerrainMode,
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                gameplay.dayNightCycleMode(),
                gameplay.dayLengthMultiplier(),
                forceMissingStronghold,
                forceMissingNetherFortress
        ).sanitized();
    }
}
