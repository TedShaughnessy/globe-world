package globe.world.util;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum TerrainMode implements StringRepresentable {
    AUTO("auto"),
    DISABLED("disabled"),
    COMPACT_TORUS("compact_torus"),
    EDGE_BLEND("edge_blend"),
    PERIODIC_LATTICE("periodic_lattice");

    public static final Codec<TerrainMode> CODEC = StringRepresentable.fromEnum(TerrainMode::values);

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

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String displayName() {
        return switch (this) {
            case AUTO -> "Auto";
            case DISABLED -> "Disabled";
            case COMPACT_TORUS -> "Compact torus";
            case EDGE_BLEND -> "Edge blend";
            case PERIODIC_LATTICE -> "Periodic lattice";
        };
    }

    public String tooltip() {
        return switch (this) {
            case AUTO -> "Auto chooses the terrain method from the tile size.";
            case DISABLED -> "Disabled means this dimension does not wrap.";
            case COMPACT_TORUS -> "Compact torus wraps tiny worlds tightly with stylized terrain.";
            case EDGE_BLEND -> "Edge blend keeps more vanilla scale in the middle and blends near tile edges.";
            case PERIODIC_LATTICE -> "Periodic lattice uses repeating noise for clean seams. Overworld noise is about 1024 chunks wide, so clean increments fit best.";
        };
    }
}
