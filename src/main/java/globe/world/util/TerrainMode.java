package globe.world.util;

public enum TerrainMode {
    DISABLED("disabled"),
    COMPACT_TORUS("compact_torus"),
    EDGE_BLEND("edge_blend"),
    PERIODIC_LATTICE("periodic_lattice");

    private static final int OVERWORLD_EDGE_BLEND_MIN_TILE_CHUNKS = 64;
    private static final int OVERWORLD_PERIODIC_LATTICE_TILE_CHUNK_MULTIPLE = 256;
    private static final int NETHER_EDGE_BLEND_MIN_TILE_CHUNKS = 32;
    private static final int NETHER_PERIODIC_LATTICE_MIN_TILE_CHUNKS = 128;
    private static final int NETHER_PERIODIC_LATTICE_TILE_CHUNK_MULTIPLE = 128;

    private final String serializedName;

    TerrainMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public static TerrainMode forOverworldTileSize(int tileChunks) {
        if (tileChunks < OVERWORLD_EDGE_BLEND_MIN_TILE_CHUNKS) {
            return COMPACT_TORUS;
        }
        if (tileChunks % OVERWORLD_PERIODIC_LATTICE_TILE_CHUNK_MULTIPLE == 0) {
            return PERIODIC_LATTICE;
        }
        return EDGE_BLEND;
    }

    public static TerrainMode forNetherTileSize(int tileChunks) {
        if (tileChunks < NETHER_EDGE_BLEND_MIN_TILE_CHUNKS) {
            return COMPACT_TORUS;
        }
        if (tileChunks >= NETHER_PERIODIC_LATTICE_MIN_TILE_CHUNKS
                && tileChunks % NETHER_PERIODIC_LATTICE_TILE_CHUNK_MULTIPLE == 0) {
            return PERIODIC_LATTICE;
        }
        return EDGE_BLEND;
    }

    public String serializedName() {
        return serializedName;
    }

    public String displayName() {
        return switch (this) {
            case DISABLED -> "Disabled";
            case COMPACT_TORUS -> "Compact torus";
            case EDGE_BLEND -> "Edge blend";
            case PERIODIC_LATTICE -> "Periodic lattice";
        };
    }
}
