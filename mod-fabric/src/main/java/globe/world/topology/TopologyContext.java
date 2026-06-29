package globe.world.topology;

import globe.world.util.ChunkAliasTracker;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class TopologyContext {
    private final ResourceKey<Level> dimension;
    private final DimensionTiling tiling;
    private final TileGeometry geometry;

    public TopologyContext(ResourceKey<Level> dimension, DimensionTiling tiling) {
        this.dimension = dimension;
        this.tiling = tiling;
        this.geometry = TileGeometry.create(tiling);
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public DimensionTiling tiling() {
        return tiling;
    }

    private TileGeometry geometry() {
        return geometry;
    }

    public boolean enabled() {
        return tiling.enabled();
    }

    public int tileSizeChunks() {
        return tiling.tileSizeChunks();
    }

    public int tileSizeBlocks() {
        return tiling.tileSizeBlocks();
    }

    public int canonicalChunkX(int rawX) {
        return CoordUtil.wrapChunk(tiling, rawX);
    }

    public int canonicalBlockX(int rawX) {
        return CoordUtil.wrapBlock(tiling, rawX);
    }

    public double canonicalBlockX(double rawX) {
        return CoordUtil.wrapBlock(tiling, rawX);
    }

    public ChunkPos canonicalChunk(int rawX, int rawZ) {
        return geometry().canonicalChunk(rawX, rawZ);
    }

    public ChunkPos canonicalChunk(ChunkPos raw) {
        return geometry().canonicalChunk(raw.x(), raw.z());
    }

    public ChunkPos canonicalChunk(SectionPos raw) {
        return canonicalChunk(raw.x(), raw.z());
    }

    public ChunkPos canonicalChunkForBlock(BlockPos raw) {
        BlockPos canonical = canonicalBlock(raw);
        return new ChunkPos(SectionPos.blockToSectionCoord(canonical.getX()), SectionPos.blockToSectionCoord(canonical.getZ()));
    }

    public BlockPos canonicalBlock(int rawX, int y, int rawZ) {
        return geometry().canonicalBlock(rawX, y, rawZ);
    }

    public BlockPos canonicalBlock(BlockPos raw) {
        return geometry().canonicalBlock(raw.getX(), raw.getY(), raw.getZ());
    }

    public Vec3 canonicalBlock(Vec3 raw) {
        return geometry().canonicalBlock(raw);
    }

    public AABB canonicalBox(AABB raw) {
        return geometry().canonicalBox(raw);
    }

    public int tileAliasChunkX(int rawX) {
        return CoordUtil.tileAliasChunk(tiling, rawX);
    }

    public int tileAliasBlockX(int rawX) {
        return CoordUtil.tileAliasBlock(tiling, rawX);
    }

    public boolean isCanonical(BlockPos pos) {
        return geometry().isCanonicalBlock(pos);
    }

    public boolean isCanonical(ChunkPos pos) {
        return geometry().isCanonicalChunk(pos);
    }

    public boolean isCanonical(SectionPos pos) {
        return canonicalChunk(pos).equals(new ChunkPos(pos.x(), pos.z()));
    }

    public int virtualChunkXForViewer(int canonicalX, int viewerChunkX) {
        return CoordUtil.virtualChunk(tiling, canonicalX, viewerChunkX);
    }

    public double virtualBlockXForViewer(double canonicalX, double viewerX) {
        return CoordUtil.virtualBlock(tiling, canonicalX, viewerX);
    }

    public ChunkPos virtualChunkForViewer(ChunkPos canonical, ChunkPos viewer) {
        return geometry().nearestAlias(canonical, viewer);
    }

    public ChunkPos virtualChunkForViewer(ChunkPos canonical, ServerPlayer viewer) {
        return virtualChunkForViewer(canonical, viewer.chunkPosition());
    }

    public ChunkPos virtualChunkForViewer(int rawX, int rawZ, ServerPlayer viewer) {
        return virtualChunkForViewer(canonicalChunk(rawX, rawZ), viewer);
    }

    public BlockPos virtualBlockForViewer(BlockPos canonical, double viewerX, double viewerZ) {
        return geometry().nearestAlias(canonical, new Vec3(viewerX, canonical.getY(), viewerZ));
    }

    public BlockPos virtualBlockForViewer(BlockPos canonical, ServerPlayer viewer) {
        return virtualBlockForViewer(canonical, viewer.getX(), viewer.getZ());
    }

    public BlockPos virtualBlockForViewer(BlockPos canonical, Vec3 viewer) {
        return virtualBlockForViewer(canonical, viewer.x(), viewer.z());
    }

    public Vec3 virtualBlockForViewer(Vec3 canonical, Vec3 viewer) {
        return geometry().nearestAlias(canonical, viewer);
    }

    public AABB virtualBoxForViewer(AABB canonical, Vec3 viewer) {
        return geometry().virtualBoxForViewer(canonical, viewer);
    }

    public List<ChunkPos> loadedAliasesFor(ServerPlayer player, ChunkPos canonicalChunk) {
        return ChunkAliasTracker.aliasesForCanonical(player, dimension, canonicalChunk.x(), canonicalChunk.z());
    }

    public boolean shouldAllowAliasMutation(ServerLevel level, BlockPos rawBlock) {
        return aliasMutationAccess(level, rawBlock).allowed();
    }

    public AliasMutationAccess aliasMutationAccess(ServerLevel level, BlockPos rawBlock) {
        BlockPos canonicalBlock = canonicalBlock(rawBlock);
        ChunkPos canonicalChunk = canonicalChunkForBlock(canonicalBlock);
        boolean alias = !canonicalBlock.equals(rawBlock);
        boolean allowed = !alias || level.shouldTickBlocksAt(canonicalChunk.pack());
        return new AliasMutationAccess(allowed, alias, canonicalBlock, canonicalChunk);
    }

    public double wrappedDeltaX(double a, double b) {
        return CoordUtil.wrappedDeltaBlock(tiling, a, b);
    }

    public double wrappedDistanceSqr(Vec3 a, Vec3 b) {
        return geometry().wrappedDistanceSqr(a, b);
    }

    public double wrappedChunkDistanceSqr(ChunkPos chunkPos, Vec3 pos) {
        return geometry().wrappedChunkDistanceSqr(chunkPos, pos);
    }

    public int wrappedChunkDistance(int a, int b) {
        return CoordUtil.wrappedChunkDistance(tiling, a, b);
    }

    public record AliasMutationAccess(
            boolean allowed,
            boolean alias,
            BlockPos canonicalBlock,
            ChunkPos canonicalChunk) {
    }
}
