package globe.world.topology;

import globe.world.util.DimensionTiling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A square canonical owner whose east/west aliases are shifted by half a tile
 * north/south. The ownership interval stays {@code [-W/2, W/2)} on both axes;
 * only the lattice translations couple X and Z.
 */
public final class OffsetSquareTileGeometry implements TileGeometry {
    private static final int NEAREST_SEARCH_RADIUS = 2;

    private final DimensionTiling tiling;
    private final int widthChunks;
    private final int halfWidthChunks;
    private final int widthBlocks;
    private final int halfWidthBlocks;
    private final AABB canonicalBlockBounds;
    private final LatticeBasis latticeBasis;
    private final Optional<LatticeBlendGeometry> blendGeometry;
    private final List<BoundarySegment> boundarySegments;

    public OffsetSquareTileGeometry(DimensionTiling tiling) {
        this.tiling = tiling;
        this.widthChunks = tiling.tileSizeChunks();
        this.halfWidthChunks = widthChunks / 2;
        this.widthBlocks = widthChunks * 16;
        this.halfWidthBlocks = halfWidthChunks * 16;
        this.canonicalBlockBounds = new AABB(
                -halfWidthBlocks,
                Double.NEGATIVE_INFINITY,
                -halfWidthBlocks,
                halfWidthBlocks,
                Double.POSITIVE_INFINITY,
                halfWidthBlocks);
        this.latticeBasis = new LatticeBasis(
                new ChunkPos(widthChunks, halfWidthChunks),
                new ChunkPos(0, widthChunks));
        this.blendGeometry = Optional.of(new LatticeBlendGeometry(latticeBasis));
        this.boundarySegments = computeBoundarySegments();
    }

    @Override
    public DimensionTiling tiling() {
        return tiling;
    }

    @Override
    public String geometryRevision() {
        return "offset-square-north-south-v1";
    }

    @Override
    public LatticeBasis latticeBasis() {
        return latticeBasis;
    }

    @Override
    public Optional<LatticeBlendGeometry> blendGeometry() {
        return blendGeometry;
    }

    @Override
    public boolean coupledLattice() {
        return true;
    }

    @Override
    public LatticeCoordinate latticeCoordinate(ChunkPos raw) {
        if (!tiling.enabled()) {
            return LatticeCoordinate.ORIGIN;
        }
        int k = Math.floorDiv(raw.x() + halfWidthChunks, widthChunks);
        int shiftedZ = raw.z() - k * halfWidthChunks;
        int l = Math.floorDiv(shiftedZ + halfWidthChunks, widthChunks);
        return new LatticeCoordinate(k, l);
    }

    @Override
    public List<LatticeCoordinate> neighboringTiles() {
        return List.of(
                new LatticeCoordinate(1, 0),
                new LatticeCoordinate(-1, 0),
                new LatticeCoordinate(0, 1),
                new LatticeCoordinate(0, -1),
                new LatticeCoordinate(1, -1),
                new LatticeCoordinate(-1, 1));
    }

    @Override
    public List<BoundarySegment> boundarySegments() {
        return boundarySegments;
    }

    @Override
    public ChunkPos canonicalChunk(int rawX, int rawZ) {
        if (!tiling.enabled()) {
            return new ChunkPos(rawX, rawZ);
        }
        LatticeCoordinate coordinate = latticeCoordinate(new ChunkPos(rawX, rawZ));
        ChunkPos translation = latticeTranslation(coordinate);
        return new ChunkPos(rawX - translation.x(), rawZ - translation.z());
    }

    @Override
    public BlockPos canonicalBlock(int rawX, int y, int rawZ) {
        if (!tiling.enabled()) {
            return new BlockPos(rawX, y, rawZ);
        }
        int rawChunkX = SectionPos.blockToSectionCoord(rawX);
        int rawChunkZ = SectionPos.blockToSectionCoord(rawZ);
        ChunkPos canonicalChunk = canonicalChunk(rawChunkX, rawChunkZ);
        int dx = (canonicalChunk.x() - rawChunkX) * 16;
        int dz = (canonicalChunk.z() - rawChunkZ) * 16;
        return dx == 0 && dz == 0 ? new BlockPos(rawX, y, rawZ) : new BlockPos(rawX + dx, y, rawZ + dz);
    }

    @Override
    public Vec3 canonicalBlock(Vec3 raw) {
        if (!tiling.enabled()) {
            return raw;
        }
        int rawChunkX = SectionPos.blockToSectionCoord((int) Math.floor(raw.x()));
        int rawChunkZ = SectionPos.blockToSectionCoord((int) Math.floor(raw.z()));
        ChunkPos canonicalChunk = canonicalChunk(rawChunkX, rawChunkZ);
        double dx = (canonicalChunk.x() - rawChunkX) * 16.0D;
        double dz = (canonicalChunk.z() - rawChunkZ) * 16.0D;
        return dx == 0.0D && dz == 0.0D ? raw : new Vec3(raw.x() + dx, raw.y(), raw.z() + dz);
    }

    @Override
    public boolean isCanonicalChunk(ChunkPos pos) {
        return !tiling.enabled()
                || pos.x() >= -halfWidthChunks && pos.x() < halfWidthChunks
                && pos.z() >= -halfWidthChunks && pos.z() < halfWidthChunks;
    }

    @Override
    public boolean isCanonicalBlock(BlockPos pos) {
        return !tiling.enabled() || isCanonicalChunk(new ChunkPos(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ())));
    }

    @Override
    public ChunkPos nearestAlias(ChunkPos canonical, ChunkPos viewer) {
        if (!tiling.enabled()) {
            return canonical;
        }
        LatticeCoordinate coordinate = nearestLattice(
                viewer.x() + 0.5D - (canonical.x() + 0.5D),
                viewer.z() + 0.5D - (canonical.z() + 0.5D),
                widthChunks,
                halfWidthChunks);
        ChunkPos translation = latticeTranslation(coordinate);
        return new ChunkPos(canonical.x() + translation.x(), canonical.z() + translation.z());
    }

    @Override
    public BlockPos nearestAlias(BlockPos canonical, Vec3 viewer) {
        if (!tiling.enabled()) {
            return canonical;
        }
        LatticeCoordinate coordinate = nearestLattice(
                viewer.x() - canonical.getX(),
                viewer.z() - canonical.getZ(),
                widthBlocks,
                halfWidthBlocks);
        ChunkPos translation = latticeTranslation(coordinate);
        return coordinate.isOrigin()
                ? canonical
                : canonical.offset(translation.x() * 16, 0, translation.z() * 16);
    }

    @Override
    public Vec3 nearestAlias(Vec3 canonical, Vec3 viewer) {
        if (!tiling.enabled()) {
            return canonical;
        }
        LatticeCoordinate coordinate = nearestLattice(
                viewer.x() - canonical.x(),
                viewer.z() - canonical.z(),
                widthBlocks,
                halfWidthBlocks);
        ChunkPos translation = latticeTranslation(coordinate);
        return coordinate.isOrigin()
                ? canonical
                : canonical.add(translation.x() * 16.0D, 0.0D, translation.z() * 16.0D);
    }

    @Override
    public AABB canonicalBox(AABB visibleBox) {
        if (!tiling.enabled()) {
            return visibleBox;
        }
        Vec3 visibleCenter = visibleBox.getCenter();
        Vec3 canonicalCenter = canonicalBlock(visibleCenter);
        double dx = canonicalCenter.x() - visibleCenter.x();
        double dz = canonicalCenter.z() - visibleCenter.z();
        return dx == 0.0D && dz == 0.0D ? visibleBox : visibleBox.move(dx, 0.0D, dz);
    }

    @Override
    public AABB virtualBoxForViewer(AABB canonicalBox, Vec3 viewer) {
        if (!tiling.enabled()) {
            return canonicalBox;
        }
        Vec3 center = canonicalBox.getCenter();
        Vec3 aliasCenter = nearestAlias(center, viewer);
        return canonicalBox.move(aliasCenter.x() - center.x(), 0.0D, aliasCenter.z() - center.z());
    }

    @Override
    public double wrappedDistanceSqr(Vec3 a, Vec3 b) {
        if (!tiling.enabled()) {
            return a.distanceToSqr(b);
        }
        Vec3 canonicalA = canonicalBlock(a);
        Vec3 canonicalB = canonicalBlock(b);
        return canonicalA.distanceToSqr(nearestAlias(canonicalB, canonicalA));
    }

    @Override
    public double wrappedChunkDistanceSqr(ChunkPos chunk, Vec3 pos) {
        Vec3 center = new Vec3(chunk.getMiddleBlockX(), pos.y(), chunk.getMiddleBlockZ());
        return wrappedDistanceSqr(center, pos);
    }

    @Override
    public List<AABB> canonicalQueryBoxes(AABB visibleBox) {
        if (!tiling.enabled()) {
            return List.of(visibleBox);
        }
        return LatticeMath.canonicalQueryBoxes(visibleBox, canonicalBlockBounds, latticeBasis);
    }

    @Override
    public List<AABB> nearbyAliasBoxes(AABB canonicalBox, Vec3 viewer, int latticeRadius) {
        if (!tiling.enabled()) {
            return List.of(canonicalBox);
        }
        Vec3 center = canonicalBox.getCenter();
        AABB nearest = virtualBoxForViewer(canonicalBox, viewer);
        Vec3 nearestCenter = nearest.getCenter();
        LatticeCoordinate base = nearestLattice(
                nearestCenter.x() - center.x(),
                nearestCenter.z() - center.z(),
                widthBlocks,
                halfWidthBlocks);
        int radius = Math.max(0, latticeRadius);
        List<AABB> boxes = new ArrayList<>();
        for (int dk = -radius; dk <= radius; dk++) {
            for (int dl = -radius; dl <= radius; dl++) {
                ChunkPos translation = latticeTranslation(
                        new LatticeCoordinate(base.k() + dk, base.l() + dl));
                boxes.add(canonicalBox.move(
                        translation.x() * 16.0D,
                        0.0D,
                        translation.z() * 16.0D));
            }
        }
        return boxes;
    }

    private List<BoundarySegment> computeBoundarySegments() {
        double min = -halfWidthBlocks;
        double max = halfWidthBlocks;
        return List.of(
                new BoundarySegment(min, min, min, 0.0D, Direction.WEST, new LatticeCoordinate(-1, 0)),
                new BoundarySegment(min, 0.0D, min, max, Direction.WEST, new LatticeCoordinate(-1, 1)),
                new BoundarySegment(max, min, max, 0.0D, Direction.EAST, new LatticeCoordinate(1, -1)),
                new BoundarySegment(max, 0.0D, max, max, Direction.EAST, new LatticeCoordinate(1, 0)),
                new BoundarySegment(min, min, max, min, Direction.NORTH, new LatticeCoordinate(0, -1)),
                new BoundarySegment(min, max, max, max, Direction.SOUTH, new LatticeCoordinate(0, 1)));
    }

    private LatticeCoordinate nearestLattice(double x, double z, int width, int halfWidth) {
        double approximateK = x / width;
        double approximateL = (z - approximateK * halfWidth) / width;
        int centerK = (int) Math.rint(approximateK);
        int centerL = (int) Math.rint(approximateL);
        LatticeCoordinate best = LatticeCoordinate.ORIGIN;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int k = centerK - NEAREST_SEARCH_RADIUS; k <= centerK + NEAREST_SEARCH_RADIUS; k++) {
            for (int l = centerL - NEAREST_SEARCH_RADIUS; l <= centerL + NEAREST_SEARCH_RADIUS; l++) {
                double dx = x - k * (double) width;
                double dz = z - (k * (double) halfWidth + l * (double) width);
                double distance = dx * dx + dz * dz;
                LatticeCoordinate candidate = new LatticeCoordinate(k, l);
                if (distance < bestDistance
                        || distance == bestDistance && compareCoordinates(candidate, best) < 0) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static int compareCoordinates(LatticeCoordinate a, LatticeCoordinate b) {
        int originComparison = Boolean.compare(!a.isOrigin(), !b.isOrigin());
        if (originComparison != 0) {
            return originComparison;
        }
        int lengthComparison = Integer.compare(
                a.k() * a.k() + a.l() * a.l(),
                b.k() * b.k() + b.l() * b.l());
        if (lengthComparison != 0) {
            return lengthComparison;
        }
        int kComparison = Integer.compare(a.k(), b.k());
        return kComparison != 0 ? kComparison : Integer.compare(a.l(), b.l());
    }

}
