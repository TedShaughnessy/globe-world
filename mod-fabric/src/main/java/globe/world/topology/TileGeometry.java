package globe.world.topology;

import globe.world.config.TilingMode;
import globe.world.util.DimensionTiling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public interface TileGeometry {
    static TileGeometry create(DimensionTiling tiling) {
        return TileGeometryCache.GEOMETRIES.computeIfAbsent(tiling, key ->
                key.mode() == TilingMode.HEX
                        ? new HexTileGeometry(key)
                        : new SquareTileGeometry(key));
    }

    DimensionTiling tiling();

    String geometryRevision();

    LatticeBasis latticeBasis();

    LatticeCoordinate latticeCoordinate(ChunkPos raw);

    List<LatticeCoordinate> neighboringTiles();

    List<BoundarySegment> boundarySegments();

    ChunkPos canonicalChunk(int rawX, int rawZ);

    BlockPos canonicalBlock(int rawX, int y, int rawZ);

    Vec3 canonicalBlock(Vec3 raw);

    boolean isCanonicalChunk(ChunkPos pos);

    boolean isCanonicalBlock(BlockPos pos);

    ChunkPos nearestAlias(ChunkPos canonical, ChunkPos viewer);

    BlockPos nearestAlias(BlockPos canonical, Vec3 viewer);

    Vec3 nearestAlias(Vec3 canonical, Vec3 viewer);

    AABB canonicalBox(AABB visibleBox);

    AABB virtualBoxForViewer(AABB canonicalBox, Vec3 viewer);

    double wrappedDistanceSqr(Vec3 a, Vec3 b);

    double wrappedChunkDistanceSqr(ChunkPos chunk, Vec3 pos);

    List<AABB> canonicalQueryBoxes(AABB visibleBox);

    default List<AABB> nearbyAliasBoxes(AABB canonicalBox, Vec3 viewer, int latticeRadius) {
        return List.of(virtualBoxForViewer(canonicalBox, viewer));
    }

    default boolean enabled() {
        return tiling().enabled();
    }

    default int tileSizeChunks() {
        return tiling().tileSizeChunks();
    }

    default int tileSizeBlocks() {
        return tiling().tileSizeBlocks();
    }

    default ChunkPos latticeTranslation(LatticeCoordinate coordinate) {
        return latticeBasis().translation(coordinate);
    }

    default Vec3 translatedAlias(Vec3 canonical, LatticeCoordinate coordinate) {
        ChunkPos translation = latticeTranslation(coordinate);
        return canonical.add(translation.x() * 16.0D, 0.0D, translation.z() * 16.0D);
    }

    default BoundaryHit nearestBoundary(Vec3 raw) {
        Vec3 canonical = canonicalBlock(raw);
        return boundarySegments().stream()
                .map(segment -> segment.hitFrom(canonical))
                .min(Comparator.comparingDouble(BoundaryHit::distance))
                .orElseThrow(() -> new IllegalStateException("Enabled tile geometry has no boundary segments"));
    }

    record LatticeCoordinate(int k, int l) {
        public static final LatticeCoordinate ORIGIN = new LatticeCoordinate(0, 0);

        public LatticeCoordinate add(LatticeCoordinate other) {
            return new LatticeCoordinate(k + other.k, l + other.l);
        }

        public boolean isOrigin() {
            return k == 0 && l == 0;
        }

        public String seamLabel() {
            if (k == 1 && l == 0) {
                return "+A";
            }
            if (k == -1 && l == 0) {
                return "-A";
            }
            if (k == 0 && l == 1) {
                return "+B";
            }
            if (k == 0 && l == -1) {
                return "-B";
            }
            if (k == 1 && l == -1) {
                return "+(A-B)";
            }
            if (k == -1 && l == 1) {
                return "-(A-B)";
            }
            return "(" + signed(k) + "," + signed(l) + ")";
        }

        public int seamPair() {
            if (l == 0 && k != 0) {
                return 0;
            }
            if (k == 0 && l != 0) {
                return 1;
            }
            return 2;
        }

        private static String signed(int value) {
            return value >= 0 ? "+" + value : Integer.toString(value);
        }
    }

    record LatticeBasis(ChunkPos a, ChunkPos b) {
        public ChunkPos translation(LatticeCoordinate coordinate) {
            return new ChunkPos(
                    coordinate.k() * a.x() + coordinate.l() * b.x(),
                    coordinate.k() * a.z() + coordinate.l() * b.z());
        }

    }

    record BoundarySegment(
            double x0,
            double z0,
            double x1,
            double z1,
            Direction outward,
            LatticeCoordinate outsideAlias) {
        public double midpointX() {
            return (x0 + x1) * 0.5D;
        }

        public double midpointZ() {
            return (z0 + z1) * 0.5D;
        }

        public Vec3 insidePoint(double y, double inset) {
            double distance = Math.max(0.0D, inset) + 0.5D;
            return new Vec3(
                    midpointX() - outward.getStepX() * distance,
                    y,
                    midpointZ() - outward.getStepZ() * distance);
        }

        public BoundaryHit hitFrom(Vec3 canonical) {
            double nearestX = Math.clamp(canonical.x(), Math.min(x0, x1), Math.max(x0, x1));
            double nearestZ = Math.clamp(canonical.z(), Math.min(z0, z1), Math.max(z0, z1));
            double dx = canonical.x() - nearestX;
            double dz = canonical.z() - nearestZ;
            return new BoundaryHit(this, canonical, nearestX, nearestZ, Math.sqrt(dx * dx + dz * dz));
        }
    }

    record BoundaryHit(
            BoundarySegment segment,
            Vec3 canonicalPosition,
            double boundaryX,
            double boundaryZ,
            double distance) {
    }
}

final class TileGeometryCache {
    static final ConcurrentMap<DimensionTiling, TileGeometry> GEOMETRIES = new ConcurrentHashMap<>();

    private TileGeometryCache() {
    }
}
