package globe.world.topology;

import globe.world.config.TopologySettings;
import globe.world.util.DimensionTiling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class HexTileGeometry implements TileGeometry {
    private static final int NEAREST_SEARCH_RADIUS = 2;

    private final DimensionTiling tiling;
    private final int widthChunks;
    private final int horizontalSpacingChunks;
    private final int heightChunks;
    private final int halfHeightChunks;
    private final int widthBlocks;
    private final int horizontalSpacingBlocks;
    private final int heightBlocks;
    private final int halfHeightBlocks;
    private final Bounds canonicalChunkBounds;
    private final AABB canonicalBlockBounds;
    private final LatticeBasis latticeBasis;
    private final Optional<LatticeBlendGeometry> blendGeometry;
    private final List<BoundarySegment> boundarySegments;

    public HexTileGeometry(DimensionTiling tiling) {
        this.tiling = tiling;
        this.widthChunks = TopologySettings.sanitizeHexTileSize(tiling.tileSizeChunks());
        this.horizontalSpacingChunks = widthChunks * 3 / 4;
        this.heightChunks = hexHeightChunks(widthChunks);
        this.halfHeightChunks = heightChunks / 2;
        this.widthBlocks = widthChunks * 16;
        this.horizontalSpacingBlocks = horizontalSpacingChunks * 16;
        this.heightBlocks = heightChunks * 16;
        this.halfHeightBlocks = halfHeightChunks * 16;
        this.canonicalChunkBounds = computeCanonicalChunkBounds();
        this.canonicalBlockBounds = new AABB(
                canonicalChunkBounds.minX() * 16.0D,
                Double.NEGATIVE_INFINITY,
                canonicalChunkBounds.minZ() * 16.0D,
                (canonicalChunkBounds.maxX() + 1) * 16.0D,
                Double.POSITIVE_INFINITY,
                (canonicalChunkBounds.maxZ() + 1) * 16.0D);
        this.latticeBasis = new LatticeBasis(latticeA(), latticeB());
        this.blendGeometry = Optional.of(new LatticeBlendGeometry(this.latticeBasis));
        this.boundarySegments = computeBoundarySegments();
    }

    @Override
    public DimensionTiling tiling() {
        return tiling;
    }

    @Override
    public String geometryRevision() {
        return "hex-east-west-v1";
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
        ChunkPos canonical = canonicalChunk(raw.x(), raw.z());
        int dx = raw.x() - canonical.x();
        int dz = raw.z() - canonical.z();
        int k = dx / horizontalSpacingChunks;
        int l = (dz - k * halfHeightChunks) / heightChunks;
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

    public int widthChunks() {
        return widthChunks;
    }

    public int heightChunks() {
        return heightChunks;
    }

    public int horizontalSpacingChunks() {
        return horizontalSpacingChunks;
    }

    public ChunkPos latticeA() {
        return new ChunkPos(horizontalSpacingChunks, halfHeightChunks);
    }

    public ChunkPos latticeB() {
        return new ChunkPos(0, heightChunks);
    }

    public ChunkPos latticeC() {
        return new ChunkPos(horizontalSpacingChunks, -halfHeightChunks);
    }

    @Override
    public ChunkPos canonicalChunk(int rawX, int rawZ) {
        if (!tiling.enabled()) {
            return new ChunkPos(rawX, rawZ);
        }
        LatticeOffset offset = nearestChunkLattice(rawX + 0.5D, rawZ + 0.5D);
        return new ChunkPos(rawX - offset.xChunks(), rawZ - offset.zChunks());
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
        return !tiling.enabled() || nearestChunkLattice(pos.x() + 0.5D, pos.z() + 0.5D).isOrigin();
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
        LatticeOffset offset = nearestChunkLattice(
                viewer.x() + 0.5D - (canonical.x() + 0.5D),
                viewer.z() + 0.5D - (canonical.z() + 0.5D));
        return new ChunkPos(canonical.x() + offset.xChunks(), canonical.z() + offset.zChunks());
    }

    @Override
    public BlockPos nearestAlias(BlockPos canonical, Vec3 viewer) {
        if (!tiling.enabled()) {
            return canonical;
        }
        LatticeOffset offset = nearestBlockLattice(viewer.x() - canonical.getX(), viewer.z() - canonical.getZ());
        return offset.isOrigin()
                ? canonical
                : canonical.offset(offset.xBlocks(), 0, offset.zBlocks());
    }

    @Override
    public Vec3 nearestAlias(Vec3 canonical, Vec3 viewer) {
        if (!tiling.enabled()) {
            return canonical;
        }
        LatticeOffset offset = nearestBlockLattice(viewer.x() - canonical.x(), viewer.z() - canonical.z());
        return offset.isOrigin()
                ? canonical
                : new Vec3(canonical.x() + offset.xBlocks(), canonical.y(), canonical.z() + offset.zBlocks());
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
        Vec3 visibleCenter = nearestAlias(center, viewer);
        double dx = visibleCenter.x() - center.x();
        double dz = visibleCenter.z() - center.z();
        return dx == 0.0D && dz == 0.0D ? canonicalBox : canonicalBox.move(dx, 0.0D, dz);
    }

    @Override
    public double wrappedDistanceSqr(Vec3 a, Vec3 b) {
        if (!tiling.enabled()) {
            return a.distanceToSqr(b);
        }
        Vec3 canonicalA = canonicalBlock(a);
        Vec3 canonicalB = canonicalBlock(b);
        Vec3 visibleB = nearestAlias(canonicalB, canonicalA);
        return canonicalA.distanceToSqr(visibleB);
    }

    @Override
    public double wrappedChunkDistanceSqr(ChunkPos chunk, Vec3 pos) {
        if (!tiling.enabled()) {
            double dx = chunk.getMiddleBlockX() - pos.x();
            double dz = chunk.getMiddleBlockZ() - pos.z();
            return dx * dx + dz * dz;
        }
        Vec3 chunkCenter = new Vec3(chunk.getMiddleBlockX(), pos.y(), chunk.getMiddleBlockZ());
        return wrappedDistanceSqr(chunkCenter, pos);
    }

    @Override
    public List<AABB> canonicalQueryBoxes(AABB visibleBox) {
        if (!tiling.enabled()) {
            return List.of(visibleBox);
        }

        if (visibleBox.getXsize() >= widthBlocks || visibleBox.getZsize() >= heightBlocks) {
            return List.of(canonicalBlockBoundsForY(visibleBox.minY, visibleBox.maxY));
        }

        Vec3 center = visibleBox.getCenter();
        LatticeOffset base = nearestBlockLattice(center.x(), center.z());
        int radius = queryOffsetRadius(visibleBox);
        Map<String, AABB> boxes = new LinkedHashMap<>();
        for (int dk = -radius; dk <= radius; dk++) {
            for (int dl = -radius; dl <= radius; dl++) {
                LatticeOffset offset = LatticeOffset.from(
                        base.k() + dk,
                        base.l() + dl,
                        horizontalSpacingChunks,
                        heightChunks,
                        halfHeightChunks);
                AABB canonicalCandidate = visibleBox.move(-offset.xBlocks(), 0.0D, -offset.zBlocks());
                if (!intersectsCanonicalBounds(canonicalCandidate)) {
                    continue;
                }
                boxes.putIfAbsent(boxKey(canonicalCandidate), canonicalCandidate);
            }
        }
        return boxes.isEmpty() ? List.of(canonicalBox(visibleBox)) : List.copyOf(boxes.values());
    }

    public List<BlockPos> aliasesAround(BlockPos canonical, Vec3 viewer, int radius) {
        if (!tiling.enabled()) {
            return List.of(canonical);
        }
        BlockPos nearest = nearestAlias(canonical, viewer);
        LatticeOffset base = nearestBlockLattice(nearest.getX() - canonical.getX(), nearest.getZ() - canonical.getZ());
        int clampedRadius = Math.max(0, radius);
        List<BlockPos> aliases = new ArrayList<>();
        for (int dk = -clampedRadius; dk <= clampedRadius; dk++) {
            for (int dl = -clampedRadius; dl <= clampedRadius; dl++) {
                LatticeOffset offset = LatticeOffset.from(
                        base.k() + dk,
                        base.l() + dl,
                        horizontalSpacingChunks,
                        heightChunks,
                        halfHeightChunks);
                aliases.add(canonical.offset(offset.xBlocks(), 0, offset.zBlocks()));
            }
        }
        return aliases;
    }

    @Override
    public List<AABB> nearbyAliasBoxes(AABB canonicalBox, Vec3 viewer, int latticeRadius) {
        if (!tiling.enabled()) {
            return List.of(canonicalBox);
        }

        Vec3 center = canonicalBox.getCenter();
        AABB nearest = virtualBoxForViewer(canonicalBox, viewer);
        Vec3 nearestCenter = nearest.getCenter();
        LatticeOffset base = nearestBlockLattice(nearestCenter.x() - center.x(), nearestCenter.z() - center.z());
        int radius = Math.max(0, latticeRadius);
        List<AABB> boxes = new ArrayList<>();
        for (int dk = -radius; dk <= radius; dk++) {
            for (int dl = -radius; dl <= radius; dl++) {
                LatticeOffset offset = LatticeOffset.from(
                        base.k() + dk,
                        base.l() + dl,
                        horizontalSpacingChunks,
                        heightChunks,
                        halfHeightChunks);
                boxes.add(canonicalBox.move(offset.xBlocks(), 0.0D, offset.zBlocks()));
            }
        }
        return boxes;
    }

    private AABB canonicalBlockBoundsForY(double minY, double maxY) {
        return new AABB(
                canonicalBlockBounds.minX,
                minY,
                canonicalBlockBounds.minZ,
                canonicalBlockBounds.maxX,
                maxY,
                canonicalBlockBounds.maxZ);
    }

    private boolean intersectsCanonicalBounds(AABB box) {
        return box.maxX > canonicalBlockBounds.minX
                && box.minX < canonicalBlockBounds.maxX
                && box.maxZ > canonicalBlockBounds.minZ
                && box.minZ < canonicalBlockBounds.maxZ;
    }

    private int queryOffsetRadius(AABB box) {
        double span = Math.max(box.getXsize() / Math.max(1, widthBlocks), box.getZsize() / Math.max(1, heightBlocks));
        return Math.max(NEAREST_SEARCH_RADIUS, (int) Math.ceil(span) + NEAREST_SEARCH_RADIUS);
    }

    private LatticeOffset nearestChunkLattice(double x, double z) {
        return nearestLattice(x, z, horizontalSpacingChunks, heightChunks, halfHeightChunks);
    }

    private LatticeOffset nearestBlockLattice(double x, double z) {
        return nearestLattice(
                x,
                z,
                horizontalSpacingBlocks,
                heightBlocks,
                halfHeightBlocks,
                horizontalSpacingChunks,
                heightChunks,
                halfHeightChunks);
    }

    private static LatticeOffset nearestLattice(double x, double z, int width, int height, int halfHeight) {
        return nearestLattice(x, z, width, height, halfHeight, width, height, halfHeight);
    }

    private static LatticeOffset nearestLattice(
            double x,
            double z,
            int unitWidth,
            int unitHeight,
            int unitHalfHeight,
            int chunkWidth,
            int chunkHeight,
            int chunkHalfHeight) {
        double approxK = x / (double) unitWidth;
        double approxL = (z - approxK * unitHalfHeight) / (double) unitHeight;
        int centerK = (int) Math.rint(approxK);
        int centerL = (int) Math.rint(approxL);
        LatticeOffset best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int k = centerK - NEAREST_SEARCH_RADIUS; k <= centerK + NEAREST_SEARCH_RADIUS; k++) {
            for (int l = centerL - NEAREST_SEARCH_RADIUS; l <= centerL + NEAREST_SEARCH_RADIUS; l++) {
                int offsetX = k * unitWidth;
                int offsetZ = k * unitHalfHeight + l * unitHeight;
                double dx = x - offsetX;
                double dz = z - offsetZ;
                double distance = dx * dx + dz * dz;
                LatticeOffset candidate = LatticeOffset.from(k, l, chunkWidth, chunkHeight, chunkHalfHeight);
                if (best == null || distance < bestDistance || (distance == bestDistance && candidate.compareTo(best) < 0)) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best == null ? LatticeOffset.ORIGIN : best;
    }

    private Bounds computeCanonicalChunkBounds() {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int x = -widthChunks; x <= widthChunks; x++) {
            for (int z = -heightChunks; z <= heightChunks; z++) {
                if (!isCanonicalChunk(new ChunkPos(x, z))) {
                    continue;
                }
                minX = Math.min(minX, x);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxZ = Math.max(maxZ, z);
            }
        }
        if (minX == Integer.MAX_VALUE) {
            return new Bounds(0, 0, 0, 0);
        }
        return new Bounds(minX, minZ, maxX, maxZ);
    }

    private List<BoundarySegment> computeBoundarySegments() {
        List<BoundarySegment> segments = new ArrayList<>();
        for (int z = canonicalChunkBounds.minZ(); z <= canonicalChunkBounds.maxZ(); z++) {
            for (int x = canonicalChunkBounds.minX(); x <= canonicalChunkBounds.maxX(); x++) {
                ChunkPos owner = new ChunkPos(x, z);
                if (!isCanonicalChunk(owner)) {
                    continue;
                }
                addBoundarySegment(segments, owner, Direction.NORTH);
                addBoundarySegment(segments, owner, Direction.SOUTH);
                addBoundarySegment(segments, owner, Direction.WEST);
                addBoundarySegment(segments, owner, Direction.EAST);
            }
        }
        return List.copyOf(segments);
    }

    private void addBoundarySegment(List<BoundarySegment> segments, ChunkPos owner, Direction outward) {
        ChunkPos outside = new ChunkPos(owner.x() + outward.getStepX(), owner.z() + outward.getStepZ());
        if (isCanonicalChunk(outside)) {
            return;
        }

        double minX = owner.getMinBlockX();
        double minZ = owner.getMinBlockZ();
        double maxX = minX + 16.0D;
        double maxZ = minZ + 16.0D;
        LatticeCoordinate outsideAlias = latticeCoordinate(outside);
        BoundarySegment segment = switch (outward) {
            case NORTH -> new BoundarySegment(minX, minZ, maxX, minZ, outward, outsideAlias);
            case SOUTH -> new BoundarySegment(minX, maxZ, maxX, maxZ, outward, outsideAlias);
            case WEST -> new BoundarySegment(minX, minZ, minX, maxZ, outward, outsideAlias);
            case EAST -> new BoundarySegment(maxX, minZ, maxX, maxZ, outward, outsideAlias);
            default -> throw new IllegalArgumentException("Hex boundary direction must be horizontal: " + outward);
        };
        segments.add(segment);
    }

    private static int hexHeightChunks(int widthChunks) {
        int height = (int) Math.round(widthChunks * Math.sqrt(3.0D) * 0.5D);
        height = Math.max(TopologySettings.MIN_HEX_TILE_SIZE_CHUNKS / 2, height);
        return height % 2 == 0 ? height : height + 1;
    }

    private static String boxKey(AABB box) {
        return box.minX + "," + box.minY + "," + box.minZ + "," + box.maxX + "," + box.maxY + "," + box.maxZ;
    }

    private record Bounds(int minX, int minZ, int maxX, int maxZ) {
    }

    private record LatticeOffset(int k, int l, int xChunks, int zChunks) implements Comparable<LatticeOffset> {
        private static final LatticeOffset ORIGIN = new LatticeOffset(0, 0, 0, 0);

        private static LatticeOffset from(int k, int l, int widthChunks, int heightChunks, int halfHeightChunks) {
            if (k == 0 && l == 0) {
                return ORIGIN;
            }
            return new LatticeOffset(k, l, k * widthChunks, k * halfHeightChunks + l * heightChunks);
        }

        private boolean isOrigin() {
            return k == 0 && l == 0;
        }

        private int xBlocks() {
            return xChunks * 16;
        }

        private int zBlocks() {
            return zChunks * 16;
        }

        @Override
        public int compareTo(LatticeOffset other) {
            int originComparison = Boolean.compare(!isOrigin(), !other.isOrigin());
            if (originComparison != 0) {
                return originComparison;
            }
            int lengthComparison = Integer.compare(k * k + l * l, other.k * other.k + other.l * other.l);
            if (lengthComparison != 0) {
                return lengthComparison;
            }
            int kComparison = Integer.compare(k, other.k);
            return kComparison != 0 ? kComparison : Integer.compare(l, other.l);
        }
    }
}
