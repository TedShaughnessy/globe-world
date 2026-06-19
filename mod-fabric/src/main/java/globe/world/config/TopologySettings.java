package globe.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import globe.world.util.TerrainMode;

import java.util.List;
import java.util.Optional;

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
        boolean forceMissingNetherFortress,
        boolean avoidWaterOnlySeeds) {
    public static final int MIN_TILE_SIZE_CHUNKS = 2;
    public static final int FORCED_STRUCTURE_SMALL_TILE_MAX_CHUNKS = 256;
    public static final int DEFAULT_NETHER_TILE_SIZE_CHUNKS = Math.max(
            MIN_TILE_SIZE_CHUNKS,
            GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS / 8
    );
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

    public static final TopologySettings DEFAULT = new TopologySettings(
            TilingMode.DISABLED,
            GlobeConfig.DEFAULT_TILE_SIZE_CHUNKS,
            TerrainMode.AUTO,
            TilingMode.DISABLED,
            TerrainMode.AUTO,
            DEFAULT_NETHER_TILE_SIZE_CHUNKS,
            DEFAULT_NETHER_PORTAL_SCALE_NUMERATOR,
            DEFAULT_NETHER_PORTAL_SCALE_DENOMINATOR,
            false,
            false,
            false
    );
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
                            Codec.BOOL.fieldOf("force_missing_nether_fortress").forGetter(TopologySettings::forceMissingNetherFortress),
                            Codec.BOOL.optionalFieldOf("avoid_water_only_seeds").forGetter(settings -> Optional.of(settings.avoidWaterOnlySeeds()))
                    ).apply(instance, (mode,
                                       tileSize,
                                       terrainMode,
                                       netherMode,
                                       netherTerrainMode,
                                       netherTileSize,
                                       netherPortalScaleNumerator,
                                       netherPortalScaleDenominator,
                                       forceMissingStronghold,
                                       forceMissingNetherFortress,
                                       avoidWaterOnlySeeds) -> new TopologySettings(
                            mode,
                            tileSize,
                            terrainMode,
                            netherMode,
                            netherTerrainMode,
                            netherTileSize,
                            netherPortalScaleNumerator,
                            netherPortalScaleDenominator,
                            forceMissingStronghold,
                            forceMissingNetherFortress,
                            avoidWaterOnlySeeds.orElse(defaultAvoidWaterOnlySeeds(mode))
                    ))
            );

    public TopologySettings {
        int sanitizedTileSize = sanitizeTileSize(tileSize);
        int sanitizedNetherTileSize = sanitizeTileSize(netherTileSize);
        TilingMode sanitizedMode = mode != null ? mode : TilingMode.DISABLED;
        TilingMode sanitizedNetherMode = netherMode != null ? netherMode : TilingMode.DISABLED;
        PortalScale scale = sanitizeNetherPortalScale(netherPortalScaleNumerator, netherPortalScaleDenominator);
        mode = sanitizedMode;
        tileSize = sanitizedTileSize;
        terrainMode = sanitizeTerrainMode(sanitizedMode, terrainMode);
        netherMode = sanitizedNetherMode;
        netherTerrainMode = sanitizeTerrainMode(sanitizedNetherMode, netherTerrainMode);
        netherTileSize = sanitizedNetherTileSize;
        netherPortalScaleNumerator = scale.numerator();
        netherPortalScaleDenominator = scale.denominator();
    }

    public static TopologySettings square(int tileSize) {
        return new TopologySettings(
                TilingMode.SQUARE,
                tileSize,
                TerrainMode.AUTO,
                TilingMode.DISABLED,
                TerrainMode.AUTO,
                defaultNetherTileSize(tileSize),
                DEFAULT_NETHER_PORTAL_SCALE_NUMERATOR,
                DEFAULT_NETHER_PORTAL_SCALE_DENOMINATOR,
                defaultForceMissingStronghold(TilingMode.SQUARE, tileSize),
                false,
                defaultAvoidWaterOnlySeeds(TilingMode.SQUARE)
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

    public TopologySettings withMode(TilingMode newMode) {
        return new TopologySettings(
                newMode,
                tileSize,
                terrainMode,
                netherMode,
                netherTerrainMode,
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                defaultForceMissingStronghold(newMode, tileSize),
                forceMissingNetherFortress,
                defaultAvoidWaterOnlySeeds(newMode)
        );
    }

    public TopologySettings withTileSize(int newTileSize) {
        return new TopologySettings(
                mode,
                newTileSize,
                TerrainMode.AUTO,
                netherMode,
                netherTerrainMode,
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                defaultForceMissingStronghold(mode, newTileSize),
                forceMissingNetherFortress,
                defaultAvoidWaterOnlySeeds(mode)
        );
    }

    public TopologySettings withTerrainMode(TerrainMode newTerrainMode) {
        return new TopologySettings(
                mode,
                tileSize,
                newTerrainMode,
                netherMode,
                netherTerrainMode,
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                forceMissingStronghold,
                forceMissingNetherFortress,
                avoidWaterOnlySeeds
        );
    }

    public TopologySettings withNetherMode(TilingMode newNetherMode) {
        return new TopologySettings(
                mode,
                tileSize,
                terrainMode,
                newNetherMode,
                TerrainMode.AUTO,
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                forceMissingStronghold,
                defaultForceMissingNetherStructure(newNetherMode, netherTileSize),
                avoidWaterOnlySeeds
        );
    }

    public TopologySettings withNetherTerrainMode(TerrainMode newNetherTerrainMode) {
        return new TopologySettings(
                mode,
                tileSize,
                terrainMode,
                netherMode,
                newNetherTerrainMode,
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                forceMissingStronghold,
                forceMissingNetherFortress,
                avoidWaterOnlySeeds
        );
    }

    public TopologySettings withNetherTileSize(int newNetherTileSize) {
        int sanitizedNetherTileSize = sanitizeTileSize(newNetherTileSize);
        return new TopologySettings(
                mode,
                tileSize,
                terrainMode,
                netherMode,
                TerrainMode.AUTO,
                sanitizedNetherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                forceMissingStronghold,
                defaultForceMissingNetherStructure(netherMode, sanitizedNetherTileSize),
                avoidWaterOnlySeeds
        );
    }

    public TopologySettings withNetherPortalScale(int numerator, int denominator) {
        PortalScale scale = sanitizeNetherPortalScale(numerator, denominator);
        return new TopologySettings(
                mode,
                tileSize,
                terrainMode,
                netherMode,
                netherTerrainMode,
                netherTileSize,
                scale.numerator(),
                scale.denominator(),
                forceMissingStronghold,
                forceMissingNetherFortress,
                avoidWaterOnlySeeds
        );
    }

    public TopologySettings withForceMissingStronghold(boolean newForceMissingStronghold) {
        return new TopologySettings(
                mode,
                tileSize,
                terrainMode,
                netherMode,
                netherTerrainMode,
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                newForceMissingStronghold,
                forceMissingNetherFortress,
                avoidWaterOnlySeeds
        );
    }

    public TopologySettings withForceMissingNetherFortress(boolean newForceMissingNetherFortress) {
        return new TopologySettings(
                mode,
                tileSize,
                terrainMode,
                netherMode,
                netherTerrainMode,
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                forceMissingStronghold,
                newForceMissingNetherFortress,
                avoidWaterOnlySeeds
        );
    }

    public TopologySettings withAvoidWaterOnlySeeds(boolean newAvoidWaterOnlySeeds) {
        return new TopologySettings(
                mode,
                tileSize,
                terrainMode,
                netherMode,
                netherTerrainMode,
                netherTileSize,
                netherPortalScaleNumerator,
                netherPortalScaleDenominator,
                forceMissingStronghold,
                forceMissingNetherFortress,
                newAvoidWaterOnlySeeds
        );
    }

    public static int defaultNetherTileSize(int overworldTileSize) {
        int sanitizedTileSize = sanitizeTileSize(overworldTileSize);
        return sanitizedTileSize >= 8 && sanitizedTileSize % 8 == 0
                ? Math.max(MIN_TILE_SIZE_CHUNKS, sanitizedTileSize / 8)
                : sanitizedTileSize;
    }

    private static TerrainMode sanitizeTerrainMode(TilingMode tilingMode, TerrainMode terrainMode) {
        if (tilingMode != TilingMode.SQUARE || terrainMode == null || terrainMode == TerrainMode.DISABLED) {
            return TerrainMode.AUTO;
        }
        return terrainMode;
    }

    public static int sanitizeTileSize(int tileSize) {
        int sanitized = Math.max(MIN_TILE_SIZE_CHUNKS, tileSize);
        if (sanitized % 2 == 0) {
            return sanitized;
        }
        return sanitized == Integer.MAX_VALUE ? sanitized - 1 : sanitized + 1;
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

    private static boolean defaultAvoidWaterOnlySeeds(TilingMode mode) {
        return mode == TilingMode.SQUARE;
    }

    private static boolean isSmallProgressionTile(int tileSize) {
        return sanitizeTileSize(tileSize) <= FORCED_STRUCTURE_SMALL_TILE_MAX_CHUNKS;
    }

    private record PortalScale(int numerator, int denominator) {
    }
}
