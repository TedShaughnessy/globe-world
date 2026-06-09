package globe.world.topology;

import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record TopologyContext(ResourceKey<Level> dimension, DimensionTiling tiling) {
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

    public ChunkPos canonicalChunk(ChunkPos raw) {
        return CoordUtil.wrapChunkPos(tiling, raw);
    }

    public BlockPos canonicalBlock(BlockPos raw) {
        return CoordUtil.wrapBlockPos(tiling, raw);
    }

    public AABB canonicalBox(AABB raw) {
        return CoordUtil.wrapAabb(tiling, raw);
    }

    public int tileAliasChunkX(int rawX) {
        return CoordUtil.tileAliasChunk(tiling, rawX);
    }

    public boolean isCanonical(BlockPos pos) {
        return CoordUtil.isInCanonicalTile(tiling, pos);
    }

    public int virtualChunkXForViewer(int canonicalX, int viewerChunkX) {
        return CoordUtil.virtualChunk(tiling, canonicalX, viewerChunkX);
    }

    public double virtualBlockXForViewer(double canonicalX, double viewerX) {
        return CoordUtil.virtualBlock(tiling, canonicalX, viewerX);
    }

    public ChunkPos virtualChunkForViewer(ChunkPos canonical, ChunkPos viewer) {
        return new ChunkPos(
                virtualChunkXForViewer(canonical.x(), viewer.x()),
                virtualChunkXForViewer(canonical.z(), viewer.z())
        );
    }

    public BlockPos virtualBlockForViewer(BlockPos canonical, Vec3 viewer) {
        int x = (int) virtualBlockXForViewer(canonical.getX(), viewer.x());
        int z = (int) virtualBlockXForViewer(canonical.getZ(), viewer.z());
        if (x == canonical.getX() && z == canonical.getZ()) {
            return canonical;
        }
        return new BlockPos(x, canonical.getY(), z);
    }

    public Vec3 virtualBlockForViewer(Vec3 canonical, Vec3 viewer) {
        double x = virtualBlockXForViewer(canonical.x(), viewer.x());
        double z = virtualBlockXForViewer(canonical.z(), viewer.z());
        if (x == canonical.x() && z == canonical.z()) {
            return canonical;
        }
        return new Vec3(x, canonical.y(), z);
    }

    public AABB virtualBoxForViewer(AABB canonical, Vec3 viewer) {
        return CoordUtil.virtualAabb(tiling, canonical, viewer.x(), viewer.z());
    }

    public double wrappedDeltaX(double a, double b) {
        return CoordUtil.wrappedDeltaBlock(tiling, a, b);
    }

    public double wrappedDistanceSqr(Vec3 a, Vec3 b) {
        return CoordUtil.wrappedDistanceSqr(tiling, a.x(), a.y(), a.z(), b.x(), b.y(), b.z());
    }

    public int wrappedChunkDistance(int a, int b) {
        return CoordUtil.wrappedChunkDistance(tiling, a, b);
    }
}
