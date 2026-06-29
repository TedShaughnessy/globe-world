package globe.world.topology;

import globe.world.util.DimensionTiling;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class SquareTileGeometry implements TileGeometry {
    private final DimensionTiling tiling;

    public SquareTileGeometry(DimensionTiling tiling) {
        this.tiling = tiling;
    }

    @Override
    public DimensionTiling tiling() {
        return tiling;
    }

    @Override
    public ChunkPos canonicalChunk(int rawX, int rawZ) {
        return new ChunkPos(wrapChunk(rawX), wrapChunk(rawZ));
    }

    @Override
    public BlockPos canonicalBlock(int rawX, int y, int rawZ) {
        return new BlockPos(wrapBlock(rawX), y, wrapBlock(rawZ));
    }

    @Override
    public Vec3 canonicalBlock(Vec3 raw) {
        if (!tiling.enabled()) {
            return raw;
        }
        double x = wrapBlock(raw.x());
        double z = wrapBlock(raw.z());
        return x == raw.x() && z == raw.z() ? raw : new Vec3(x, raw.y(), z);
    }

    @Override
    public boolean isCanonicalChunk(ChunkPos pos) {
        return !tiling.enabled() || (pos.x() == wrapChunk(pos.x()) && pos.z() == wrapChunk(pos.z()));
    }

    @Override
    public boolean isCanonicalBlock(BlockPos pos) {
        return !tiling.enabled() || (pos.getX() == wrapBlock(pos.getX()) && pos.getZ() == wrapBlock(pos.getZ()));
    }

    @Override
    public ChunkPos nearestAlias(ChunkPos canonical, ChunkPos viewer) {
        if (!tiling.enabled()) {
            return canonical;
        }
        return new ChunkPos(
                virtualChunk(canonical.x(), viewer.x()),
                virtualChunk(canonical.z(), viewer.z())
        );
    }

    @Override
    public BlockPos nearestAlias(BlockPos canonical, Vec3 viewer) {
        if (!tiling.enabled()) {
            return canonical;
        }
        int x = (int) virtualBlock(canonical.getX(), viewer.x());
        int z = (int) virtualBlock(canonical.getZ(), viewer.z());
        return x == canonical.getX() && z == canonical.getZ()
                ? canonical
                : new BlockPos(x, canonical.getY(), z);
    }

    @Override
    public Vec3 nearestAlias(Vec3 canonical, Vec3 viewer) {
        if (!tiling.enabled()) {
            return canonical;
        }
        double x = virtualBlock(canonical.x(), viewer.x());
        double z = virtualBlock(canonical.z(), viewer.z());
        return x == canonical.x() && z == canonical.z() ? canonical : new Vec3(x, canonical.y(), z);
    }

    @Override
    public AABB canonicalBox(AABB visibleBox) {
        if (!tiling.enabled()) {
            return visibleBox;
        }
        double centerX = (visibleBox.minX + visibleBox.maxX) * 0.5D;
        double centerZ = (visibleBox.minZ + visibleBox.maxZ) * 0.5D;
        double dx = wrapBlock(centerX) - centerX;
        double dz = wrapBlock(centerZ) - centerZ;
        return dx == 0.0D && dz == 0.0D ? visibleBox : visibleBox.move(dx, 0.0D, dz);
    }

    @Override
    public AABB virtualBoxForViewer(AABB canonicalBox, Vec3 viewer) {
        if (!tiling.enabled()) {
            return canonicalBox;
        }
        double centerX = (canonicalBox.minX + canonicalBox.maxX) * 0.5D;
        double centerZ = (canonicalBox.minZ + canonicalBox.maxZ) * 0.5D;
        double dx = virtualBlock(centerX, viewer.x()) - centerX;
        double dz = virtualBlock(centerZ, viewer.z()) - centerZ;
        return dx == 0.0D && dz == 0.0D ? canonicalBox : canonicalBox.move(dx, 0.0D, dz);
    }

    @Override
    public double wrappedDistanceSqr(Vec3 a, Vec3 b) {
        double dy = a.y() - b.y();
        double dx = wrappedDeltaBlock(a.x(), b.x());
        double dz = wrappedDeltaBlock(a.z(), b.z());
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public double wrappedChunkDistanceSqr(ChunkPos chunk, Vec3 pos) {
        double dx = wrappedDeltaBlock(chunk.getMiddleBlockX(), pos.x());
        double dz = wrappedDeltaBlock(chunk.getMiddleBlockZ(), pos.z());
        return dx * dx + dz * dz;
    }

    @Override
    public List<AABB> canonicalQueryBoxes(AABB visibleBox) {
        if (!tiling.enabled()) {
            return List.of(visibleBox);
        }

        List<Interval> xIntervals = canonicalIntervals(tileSizeBlocks(), visibleBox.minX, visibleBox.maxX);
        List<Interval> zIntervals = canonicalIntervals(tileSizeBlocks(), visibleBox.minZ, visibleBox.maxZ);
        List<AABB> boxes = new ArrayList<>(xIntervals.size() * zIntervals.size());
        for (Interval x : xIntervals) {
            for (Interval z : zIntervals) {
                AABB canonicalBox = new AABB(x.min(), visibleBox.minY, z.min(), x.max(), visibleBox.maxY, z.max());
                boxes.add(sameBox(canonicalBox, visibleBox) ? visibleBox : canonicalBox);
            }
        }
        return boxes;
    }

    @Override
    public List<AABB> nearbyAliasBoxes(AABB canonicalBox, Vec3 viewer, int latticeRadius) {
        if (!tiling.enabled()) {
            return List.of(canonicalBox);
        }

        int radius = Math.max(0, latticeRadius);
        int tileSize = tileSizeBlocks();
        double centerX = (canonicalBox.minX + canonicalBox.maxX) * 0.5D;
        double centerZ = (canonicalBox.minZ + canonicalBox.maxZ) * 0.5D;
        int baseTileX = virtualBlockTileOffset(centerX, viewer.x());
        int baseTileZ = virtualBlockTileOffset(centerZ, viewer.z());
        List<AABB> boxes = new ArrayList<>();
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                boxes.add(canonicalBox.move(
                        (baseTileX + offsetX) * (double) tileSize,
                        0.0D,
                        (baseTileZ + offsetZ) * (double) tileSize));
            }
        }
        return boxes;
    }

    public int wrapChunk(int c) {
        if (!tiling.enabled()) {
            return c;
        }
        int tileSize = tileSizeChunks();
        int half = tileSize / 2;
        return Math.floorMod(c + half, tileSize) - half;
    }

    public int wrapBlock(int b) {
        if (!tiling.enabled()) {
            return b;
        }
        int tileSize = tileSizeBlocks();
        int half = tileSize / 2;
        return Math.floorMod(b + half, tileSize) - half;
    }

    public double wrapBlock(double b) {
        if (!tiling.enabled()) {
            return b;
        }
        int tileSize = tileSizeBlocks();
        double half = tileSize / 2.0D;
        return positiveModulo(b + half, tileSize) - half;
    }

    public double virtualBlock(double canonical, double viewer) {
        if (!tiling.enabled()) {
            return canonical;
        }
        return canonical + virtualBlockTileOffset(canonical, viewer) * (double) tileSizeBlocks();
    }

    public int virtualBlockTileOffset(double canonical, double viewer) {
        if (!tiling.enabled()) {
            return 0;
        }
        return (int) Math.rint((viewer - canonical) / tileSizeBlocks());
    }

    public int virtualChunk(int canonical, int viewerChunk) {
        if (!tiling.enabled()) {
            return canonical;
        }
        int tileSize = tileSizeChunks();
        int k = Math.floorDiv(viewerChunk - canonical + tileSize / 2, tileSize);
        return canonical + k * tileSize;
    }

    public int wrappedChunkDistance(int a, int b) {
        if (!tiling.enabled()) {
            return Math.abs(a - b);
        }
        int tileSize = tileSizeChunks();
        int wrappedDelta = Math.floorMod(a - b, tileSize);
        return Math.min(wrappedDelta, tileSize - wrappedDelta);
    }

    public double wrappedDeltaBlock(double a, double b) {
        if (!tiling.enabled()) {
            return a - b;
        }
        double d = a - b;
        int tileSize = tileSizeBlocks();
        return d - Math.rint(d / tileSize) * tileSize;
    }

    private static List<Interval> canonicalIntervals(int tileSize, double min, double max) {
        double canonicalMin = -tileSize / 2.0D;
        double canonicalMax = canonicalMin + tileSize;
        if (max - min >= tileSize) {
            return List.of(new Interval(canonicalMin, canonicalMax));
        }

        int firstOffset = (int) Math.floor((min - canonicalMax) / tileSize);
        int lastOffset = (int) Math.floor((max - canonicalMin) / tileSize);
        List<Interval> intervals = new ArrayList<>();
        for (int offset = firstOffset; offset <= lastOffset; offset++) {
            double shiftedMin = min - offset * (double) tileSize;
            double shiftedMax = max - offset * (double) tileSize;
            double intervalMin = Math.max(shiftedMin, canonicalMin);
            double intervalMax = Math.min(shiftedMax, canonicalMax);
            if (intervalMax > intervalMin) {
                intervals.add(new Interval(intervalMin, intervalMax));
            }
        }
        return intervals.isEmpty() ? List.of(new Interval(min, max)) : intervals;
    }

    private static boolean sameBox(AABB a, AABB b) {
        return a.minX == b.minX
                && a.minY == b.minY
                && a.minZ == b.minZ
                && a.maxX == b.maxX
                && a.maxY == b.maxY
                && a.maxZ == b.maxZ;
    }

    private static double positiveModulo(double value, double modulus) {
        return value - Math.floor(value / modulus) * modulus;
    }

    private record Interval(double min, double max) {
    }
}
