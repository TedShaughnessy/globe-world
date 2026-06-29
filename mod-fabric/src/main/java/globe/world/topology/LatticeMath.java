package globe.world.topology;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Geometry-neutral calculations for an invertible two-dimensional chunk
 * lattice.
 */
public final class LatticeMath {
    private static final double BLOCKS_PER_CHUNK = 16.0D;

    private LatticeMath() {
    }

    /**
     * Returns exact canonical rectangular slices for every lattice frame that
     * intersects {@code visibleBox}. The canonical owner may be a mask inside
     * {@code canonicalBounds}; coordinates outside that mask cannot contain
     * canonical state and are harmless broad-phase space.
     */
    public static List<AABB> canonicalQueryBoxes(
            AABB visibleBox,
            AABB canonicalBounds,
            TileGeometry.LatticeBasis basis) {
        BlockBasis blockBasis = BlockBasis.from(basis);

        double minDx = visibleBox.minX - canonicalBounds.maxX;
        double maxDx = visibleBox.maxX - canonicalBounds.minX;
        double minDz = visibleBox.minZ - canonicalBounds.maxZ;
        double maxDz = visibleBox.maxZ - canonicalBounds.minZ;
        CoordinateRange range = blockBasis.coordinateRange(minDx, maxDx, minDz, maxDz);

        Map<BoxKey, AABB> boxes = new LinkedHashMap<>();
        for (int k = range.minK(); k <= range.maxK(); k++) {
            for (int l = range.minL(); l <= range.maxL(); l++) {
                double translationX = k * blockBasis.ax() + l * blockBasis.bx();
                double translationZ = k * blockBasis.az() + l * blockBasis.bz();
                AABB candidate = visibleBox.move(-translationX, 0.0D, -translationZ);
                AABB clipped = clip(candidate, canonicalBounds);
                if (clipped == null) {
                    continue;
                }
                if (coversCanonicalBounds(clipped, canonicalBounds)) {
                    return List.of(boundsForY(canonicalBounds, visibleBox.minY, visibleBox.maxY));
                }
                boxes.putIfAbsent(BoxKey.from(clipped), clipped);
            }
        }
        return List.copyOf(boxes.values());
    }

    /**
     * Bounds lattice coefficients using the two dual-basis altitudes. Unlike
     * shortest-vector distance, these bounds remain complete when oblique
     * basis vectors partially cancel.
     */
    public static CoefficientRadii coefficientRadii(
            TileGeometry.LatticeBasis basis,
            double searchDistanceBlocks) {
        BlockBasis blockBasis = BlockBasis.from(basis);
        double distance = Math.max(0.0D, searchDistanceBlocks);
        double kAltitude = Math.abs(blockBasis.determinant())
                / Math.hypot(blockBasis.bx(), blockBasis.bz());
        double lAltitude = Math.abs(blockBasis.determinant())
                / Math.hypot(blockBasis.ax(), blockBasis.az());
        return new CoefficientRadii(
                ceilToInt(distance / kAltitude),
                ceilToInt(distance / lAltitude));
    }

    public static CoefficientBounds coefficientBounds(
            TileGeometry.LatticeBasis basis,
            double centerDx,
            double centerDz,
            double searchDistanceBlocks) {
        BlockBasis blockBasis = BlockBasis.from(basis);
        CoefficientRadii radii = coefficientRadii(basis, searchDistanceBlocks);
        double centerK = (centerDx * blockBasis.bz() - centerDz * blockBasis.bx())
                / blockBasis.determinant();
        double centerL = (blockBasis.ax() * centerDz - blockBasis.az() * centerDx)
                / blockBasis.determinant();
        return new CoefficientBounds(
                ceilSignedToInt(centerK - radii.k()),
                floorToInt(centerK + radii.k()),
                ceilSignedToInt(centerL - radii.l()),
                floorToInt(centerL + radii.l()));
    }

    public static TileGeometry.LatticeCoordinate coordinateForBlockTranslation(
            TileGeometry.LatticeBasis basis,
            double dx,
            double dz) {
        BlockBasis blockBasis = BlockBasis.from(basis);
        return new TileGeometry.LatticeCoordinate(
                (int) Math.rint((dx * blockBasis.bz() - dz * blockBasis.bx()) / blockBasis.determinant()),
                (int) Math.rint((blockBasis.ax() * dz - blockBasis.az() * dx) / blockBasis.determinant()));
    }

    private static AABB clip(AABB box, AABB bounds) {
        double minX = Math.max(box.minX, bounds.minX);
        double minZ = Math.max(box.minZ, bounds.minZ);
        double maxX = Math.min(box.maxX, bounds.maxX);
        double maxZ = Math.min(box.maxZ, bounds.maxZ);
        return maxX > minX && maxZ > minZ
                ? new AABB(minX, box.minY, minZ, maxX, box.maxY, maxZ)
                : null;
    }

    private static boolean coversCanonicalBounds(AABB box, AABB bounds) {
        return box.minX <= bounds.minX
                && box.maxX >= bounds.maxX
                && box.minZ <= bounds.minZ
                && box.maxZ >= bounds.maxZ;
    }

    private static AABB boundsForY(AABB bounds, double minY, double maxY) {
        return new AABB(bounds.minX, minY, bounds.minZ, bounds.maxX, maxY, bounds.maxZ);
    }

    private static int floorToInt(double value) {
        if (value <= Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.floor(value);
    }

    private static int ceilToInt(double value) {
        if (value <= 0.0D) {
            return 0;
        }
        if (value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(value);
    }

    private static int ceilSignedToInt(double value) {
        if (value <= Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(value);
    }

    public record CoefficientRadii(int k, int l) {
        public int maximum() {
            return Math.max(k, l);
        }
    }

    public record CoefficientBounds(int minK, int maxK, int minL, int maxL) {
        public boolean contains(int k, int l) {
            return k >= minK && k <= maxK && l >= minL && l <= maxL;
        }
    }

    private record BlockBasis(double ax, double az, double bx, double bz, double determinant) {
        private static BlockBasis from(TileGeometry.LatticeBasis basis) {
            ChunkPos a = basis.a();
            ChunkPos b = basis.b();
            double ax = a.x() * BLOCKS_PER_CHUNK;
            double az = a.z() * BLOCKS_PER_CHUNK;
            double bx = b.x() * BLOCKS_PER_CHUNK;
            double bz = b.z() * BLOCKS_PER_CHUNK;
            double determinant = ax * bz - az * bx;
            if (determinant == 0.0D) {
                throw new IllegalArgumentException("Lattice basis must be invertible");
            }
            return new BlockBasis(ax, az, bx, bz, determinant);
        }

        private CoordinateRange coordinateRange(double minX, double maxX, double minZ, double maxZ) {
            double minK = Double.POSITIVE_INFINITY;
            double maxK = Double.NEGATIVE_INFINITY;
            double minL = Double.POSITIVE_INFINITY;
            double maxL = Double.NEGATIVE_INFINITY;
            for (double x : new double[]{minX, maxX}) {
                for (double z : new double[]{minZ, maxZ}) {
                    double k = (x * bz - z * bx) / determinant;
                    double l = (ax * z - az * x) / determinant;
                    minK = Math.min(minK, k);
                    maxK = Math.max(maxK, k);
                    minL = Math.min(minL, l);
                    maxL = Math.max(maxL, l);
                }
            }
            return new CoordinateRange(
                    expandDown(floorToInt(minK)),
                    expandUp(ceilToInt(maxK)),
                    expandDown(floorToInt(minL)),
                    expandUp(ceilToInt(maxL)));
        }

        private static int expandDown(int value) {
            return value == Integer.MIN_VALUE ? value : value - 1;
        }

        private static int expandUp(int value) {
            return value == Integer.MAX_VALUE ? value : value + 1;
        }
    }

    private record CoordinateRange(int minK, int maxK, int minL, int maxL) {
    }

    private record BoxKey(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ) {
        private static BoxKey from(AABB box) {
            return new BoxKey(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        }
    }
}
