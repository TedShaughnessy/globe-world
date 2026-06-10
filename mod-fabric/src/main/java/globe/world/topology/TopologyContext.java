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

    public ChunkPos canonicalChunk(int rawX, int rawZ) {
        int x = canonicalChunkX(rawX);
        int z = canonicalChunkX(rawZ);
        if (x == rawX && z == rawZ) {
            return new ChunkPos(rawX, rawZ);
        }
        return new ChunkPos(x, z);
    }

    public ChunkPos canonicalChunk(ChunkPos raw) {
        return CoordUtil.wrapChunkPos(tiling, raw);
    }

    public ChunkPos canonicalChunk(SectionPos raw) {
        return canonicalChunk(raw.x(), raw.z());
    }

    public ChunkPos canonicalChunkForBlock(BlockPos raw) {
        BlockPos canonical = canonicalBlock(raw);
        return new ChunkPos(SectionPos.blockToSectionCoord(canonical.getX()), SectionPos.blockToSectionCoord(canonical.getZ()));
    }

    public BlockPos canonicalBlock(int rawX, int y, int rawZ) {
        int x = canonicalBlockX(rawX);
        int z = canonicalBlockX(rawZ);
        if (x == rawX && z == rawZ) {
            return new BlockPos(rawX, y, rawZ);
        }
        return new BlockPos(x, y, z);
    }

    public BlockPos canonicalBlock(BlockPos raw) {
        return CoordUtil.wrapBlockPos(tiling, raw);
    }

    public Vec3 canonicalBlock(Vec3 raw) {
        double x = canonicalBlockX(raw.x());
        double z = canonicalBlockX(raw.z());
        if (x == raw.x() && z == raw.z()) {
            return raw;
        }
        return new Vec3(x, raw.y(), z);
    }

    public AABB canonicalBox(AABB raw) {
        return CoordUtil.wrapAabb(tiling, raw);
    }

    public int tileAliasChunkX(int rawX) {
        return CoordUtil.tileAliasChunk(tiling, rawX);
    }

    public int tileAliasBlockX(int rawX) {
        return CoordUtil.tileAliasBlock(tiling, rawX);
    }

    public boolean isCanonical(BlockPos pos) {
        return CoordUtil.isInCanonicalTile(tiling, pos);
    }

    public boolean isCanonical(ChunkPos pos) {
        return canonicalChunk(pos).equals(pos);
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
        return new ChunkPos(
                virtualChunkXForViewer(canonical.x(), viewer.x()),
                virtualChunkXForViewer(canonical.z(), viewer.z())
        );
    }

    public ChunkPos virtualChunkForViewer(ChunkPos canonical, ServerPlayer viewer) {
        return virtualChunkForViewer(canonical, viewer.chunkPosition());
    }

    public ChunkPos virtualChunkForViewer(int rawX, int rawZ, ServerPlayer viewer) {
        return virtualChunkForViewer(canonicalChunk(rawX, rawZ), viewer);
    }

    public BlockPos virtualBlockForViewer(BlockPos canonical, double viewerX, double viewerZ) {
        int x = (int) virtualBlockXForViewer(canonical.getX(), viewerX);
        int z = (int) virtualBlockXForViewer(canonical.getZ(), viewerZ);
        if (x == canonical.getX() && z == canonical.getZ()) {
            return canonical;
        }
        return new BlockPos(x, canonical.getY(), z);
    }

    public BlockPos virtualBlockForViewer(BlockPos canonical, ServerPlayer viewer) {
        return virtualBlockForViewer(canonical, viewer.getX(), viewer.getZ());
    }

    public BlockPos virtualBlockForViewer(BlockPos canonical, Vec3 viewer) {
        return virtualBlockForViewer(canonical, viewer.x(), viewer.z());
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
        return CoordUtil.wrappedDistanceSqr(tiling, a.x(), a.y(), a.z(), b.x(), b.y(), b.z());
    }

    public double wrappedChunkDistanceSqr(ChunkPos chunkPos, Vec3 pos) {
        return CoordUtil.wrappedChunkDistanceSqr(tiling, chunkPos, pos);
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
