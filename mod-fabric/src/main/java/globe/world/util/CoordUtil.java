package globe.world.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CoordUtil {
    public static final int MINECRAFT_DAY_TICKS = 24000;

    public static int wrapChunk(int c) {
        return wrapChunk(DimensionTiling.currentOrOverworld(), c);
    }

    public static int wrapChunk(Level level, int c) {
        return wrapChunk(DimensionTiling.forLevel(level), c);
    }

    public static int wrapChunk(ResourceKey<Level> dimension, int c) {
        return wrapChunk(DimensionTiling.forDimension(dimension), c);
    }

    public static int wrapChunk(DimensionTiling tiling, int c) {
        if (!tiling.enabled()) {
            return c;
        }
        int tileSize = tiling.tileSizeChunks();
        int half = tileSize / 2;
        return Math.floorMod(c + half, tileSize) - half;
    }

    public static int wrapBlock(int b) {
        return wrapBlock(DimensionTiling.currentOrOverworld(), b);
    }

    public static int wrapBlock(Level level, int b) {
        return wrapBlock(DimensionTiling.forLevel(level), b);
    }

    public static int wrapBlock(ResourceKey<Level> dimension, int b) {
        return wrapBlock(DimensionTiling.forDimension(dimension), b);
    }

    public static int wrapBlock(DimensionTiling tiling, int b) {
        if (!tiling.enabled()) {
            return b;
        }
        int tileSize = tiling.tileSizeBlocks();
        int half = tileSize / 2;
        return Math.floorMod(b + half, tileSize) - half;
    }

    public static double wrapBlock(double b) {
        return wrapBlock(DimensionTiling.currentOrOverworld(), b);
    }

    public static double wrapBlock(Level level, double b) {
        return wrapBlock(DimensionTiling.forLevel(level), b);
    }

    public static double wrapBlock(ResourceKey<Level> dimension, double b) {
        return wrapBlock(DimensionTiling.forDimension(dimension), b);
    }

    public static double wrapBlock(DimensionTiling tiling, double b) {
        if (!tiling.enabled()) {
            return b;
        }
        int tileSize = tiling.tileSizeBlocks();
        double half = tileSize / 2.0;
        return positiveModulo(b + half, tileSize) - half;
    }

    public static double longitudeOffsetTicks(Level level, BlockPos pos) {
        return longitudeOffsetTicks(level, pos.getX());
    }

    public static double longitudeOffsetTicks(Level level, double x) {
        return longitudeOffsetTicks(DimensionTiling.forLevel(level), x);
    }

    public static double longitudeOffsetTicks(ResourceKey<Level> dimension, double x) {
        return longitudeOffsetTicks(DimensionTiling.forDimension(dimension), x);
    }

    public static double longitudeOffsetTicks(DimensionTiling tiling, BlockPos pos) {
        return longitudeOffsetTicks(tiling, pos.getX());
    }

    public static double longitudeOffsetTicks(DimensionTiling tiling, double x) {
        if (!tiling.enabled()) {
            return 0.0;
        }
        return wrapBlock(tiling, x) * MINECRAFT_DAY_TICKS / tiling.tileSizeBlocks();
    }

    public static double localSolarTimeTicks(Level level, BlockPos pos) {
        return localSolarTimeTicks(level, pos.getX());
    }

    public static double localSolarTimeTicks(Level level, double x) {
        return localSolarTimeTicks(DimensionTiling.forLevel(level), level.getDefaultClockTime(), x);
    }

    public static double localSolarTimeTicks(ResourceKey<Level> dimension, long worldTime, double x) {
        return localSolarTimeTicks(DimensionTiling.forDimension(dimension), worldTime, x);
    }

    public static double localSolarTimeTicks(DimensionTiling tiling, long worldTime, BlockPos pos) {
        return localSolarTimeTicks(tiling, worldTime, pos.getX());
    }

    public static double localSolarTimeTicks(DimensionTiling tiling, long worldTime, double x) {
        return worldTime + longitudeOffsetTicks(tiling, x);
    }

    public static double localSolarDayTicks(Level level, BlockPos pos) {
        return localSolarDayTicks(level, pos.getX());
    }

    public static double localSolarDayTicks(Level level, double x) {
        return localSolarDayTicks(DimensionTiling.forLevel(level), level.getDefaultClockTime(), x);
    }

    public static double localSolarDayTicks(ResourceKey<Level> dimension, long worldTime, double x) {
        return localSolarDayTicks(DimensionTiling.forDimension(dimension), worldTime, x);
    }

    public static double localSolarDayTicks(DimensionTiling tiling, long worldTime, BlockPos pos) {
        return localSolarDayTicks(tiling, worldTime, pos.getX());
    }

    public static double localSolarDayTicks(DimensionTiling tiling, long worldTime, double x) {
        return positiveModulo(localSolarTimeTicks(tiling, worldTime, x), MINECRAFT_DAY_TICKS);
    }

    public static BlockPos wrapBlockPos(BlockPos pos) {
        return wrapBlockPos(DimensionTiling.currentOrOverworld(), pos);
    }

    public static BlockPos wrapBlockPos(Level level, BlockPos pos) {
        return wrapBlockPos(DimensionTiling.forLevel(level), pos);
    }

    public static BlockPos wrapBlockPos(ResourceKey<Level> dimension, BlockPos pos) {
        return wrapBlockPos(DimensionTiling.forDimension(dimension), pos);
    }

    public static BlockPos wrapBlockPos(DimensionTiling tiling, BlockPos pos) {
        int x = wrapBlock(tiling, pos.getX());
        int z = wrapBlock(tiling, pos.getZ());
        if (x == pos.getX() && z == pos.getZ()) {
            return pos;
        }
        return new BlockPos(x, pos.getY(), z);
    }

    public static ChunkPos wrapChunkPos(ChunkPos pos) {
        return wrapChunkPos(DimensionTiling.currentOrOverworld(), pos);
    }

    public static ChunkPos wrapChunkPos(Level level, ChunkPos pos) {
        return wrapChunkPos(DimensionTiling.forLevel(level), pos);
    }

    public static ChunkPos wrapChunkPos(ResourceKey<Level> dimension, ChunkPos pos) {
        return wrapChunkPos(DimensionTiling.forDimension(dimension), pos);
    }

    public static ChunkPos wrapChunkPos(DimensionTiling tiling, ChunkPos pos) {
        int x = wrapChunk(tiling, pos.x());
        int z = wrapChunk(tiling, pos.z());
        if (x == pos.x() && z == pos.z()) {
            return pos;
        }
        return new ChunkPos(x, z);
    }

    public static int tileAliasChunk(int chunk) {
        return tileAliasChunk(DimensionTiling.currentOrOverworld(), chunk);
    }

    public static int tileAliasChunk(Level level, int chunk) {
        return tileAliasChunk(DimensionTiling.forLevel(level), chunk);
    }

    public static int tileAliasChunk(DimensionTiling tiling, int chunk) {
        if (!tiling.enabled()) {
            return 0;
        }
        return (chunk - wrapChunk(tiling, chunk)) / tiling.tileSizeChunks();
    }

    public static int tileAliasBlock(int block) {
        return tileAliasBlock(DimensionTiling.currentOrOverworld(), block);
    }

    public static int tileAliasBlock(Level level, int block) {
        return tileAliasBlock(DimensionTiling.forLevel(level), block);
    }

    public static int tileAliasBlock(DimensionTiling tiling, int block) {
        if (!tiling.enabled()) {
            return 0;
        }
        return (block - wrapBlock(tiling, block)) / tiling.tileSizeBlocks();
    }

    public static boolean isInCanonicalTile(BlockPos pos) {
        return isInCanonicalTile(DimensionTiling.currentOrOverworld(), pos);
    }

    public static boolean isInCanonicalTile(Level level, BlockPos pos) {
        return isInCanonicalTile(DimensionTiling.forLevel(level), pos);
    }

    public static boolean isInCanonicalTile(DimensionTiling tiling, BlockPos pos) {
        return !tiling.enabled()
                || (pos.getX() == wrapBlock(tiling, pos.getX()) && pos.getZ() == wrapBlock(tiling, pos.getZ()));
    }

    public static double wrappedDeltaBlock(double a, double b) {
        return wrappedDeltaBlock(DimensionTiling.currentOrOverworld(), a, b);
    }

    public static double wrappedDeltaBlock(Level level, double a, double b) {
        return wrappedDeltaBlock(DimensionTiling.forLevel(level), a, b);
    }

    public static double wrappedDeltaBlock(DimensionTiling tiling, double a, double b) {
        if (!tiling.enabled()) {
            return a - b;
        }
        double d = a - b;
        int tileSize = tiling.tileSizeBlocks();
        return d - Math.rint(d / tileSize) * tileSize;
    }

    public static double wrappedDistanceSqrXZ(double ax, double az, double bx, double bz) {
        return wrappedDistanceSqrXZ(DimensionTiling.currentOrOverworld(), ax, az, bx, bz);
    }

    public static double wrappedDistanceSqrXZ(Level level, double ax, double az, double bx, double bz) {
        return wrappedDistanceSqrXZ(DimensionTiling.forLevel(level), ax, az, bx, bz);
    }

    public static double wrappedDistanceSqrXZ(DimensionTiling tiling, double ax, double az, double bx, double bz) {
        double dx = wrappedDeltaBlock(tiling, ax, bx);
        double dz = wrappedDeltaBlock(tiling, az, bz);
        return dx * dx + dz * dz;
    }

    public static double wrappedDistanceSqr(double ax, double ay, double az, double bx, double by, double bz) {
        return wrappedDistanceSqr(DimensionTiling.currentOrOverworld(), ax, ay, az, bx, by, bz);
    }

    public static double wrappedDistanceSqr(Level level, double ax, double ay, double az, double bx, double by, double bz) {
        return wrappedDistanceSqr(DimensionTiling.forLevel(level), ax, ay, az, bx, by, bz);
    }

    public static double wrappedDistanceSqr(DimensionTiling tiling, double ax, double ay, double az, double bx, double by, double bz) {
        double dy = ay - by;
        return wrappedDistanceSqrXZ(tiling, ax, az, bx, bz) + dy * dy;
    }

    public static double wrappedDistanceSqr(Entity a, Entity b) {
        return wrappedDistanceSqr(a.level(), a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    public static double wrappedChunkDistanceSqr(ChunkPos chunkPos, Vec3 pos) {
        return wrappedChunkDistanceSqr(DimensionTiling.currentOrOverworld(), chunkPos, pos);
    }

    public static double wrappedChunkDistanceSqr(Level level, ChunkPos chunkPos, Vec3 pos) {
        return wrappedChunkDistanceSqr(DimensionTiling.forLevel(level), chunkPos, pos);
    }

    public static double wrappedChunkDistanceSqr(DimensionTiling tiling, ChunkPos chunkPos, Vec3 pos) {
        return wrappedDistanceSqrXZ(tiling, chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ(), pos.x, pos.z);
    }

    public static double virtualBlock(double canonical, double viewer) {
        return virtualBlock(DimensionTiling.currentOrOverworld(), canonical, viewer);
    }

    public static double virtualBlock(Level level, double canonical, double viewer) {
        return virtualBlock(DimensionTiling.forLevel(level), canonical, viewer);
    }

    public static double virtualBlock(DimensionTiling tiling, double canonical, double viewer) {
        if (!tiling.enabled()) {
            return canonical;
        }
        int tileSize = tiling.tileSizeBlocks();
        return canonical + virtualBlockTileOffset(tiling, canonical, viewer) * (double) tileSize;
    }

    public static int virtualBlockTileOffset(double canonical, double viewer) {
        return virtualBlockTileOffset(DimensionTiling.currentOrOverworld(), canonical, viewer);
    }

    public static int virtualBlockTileOffset(Level level, double canonical, double viewer) {
        return virtualBlockTileOffset(DimensionTiling.forLevel(level), canonical, viewer);
    }

    public static int virtualBlockTileOffset(DimensionTiling tiling, double canonical, double viewer) {
        if (!tiling.enabled()) {
            return 0;
        }
        return (int) Math.rint((viewer - canonical) / tiling.tileSizeBlocks());
    }

    public static AABB virtualAabb(AABB canonical, double viewerX, double viewerZ) {
        return virtualAabb(DimensionTiling.currentOrOverworld(), canonical, viewerX, viewerZ);
    }

    public static AABB virtualAabb(Level level, AABB canonical, double viewerX, double viewerZ) {
        return virtualAabb(DimensionTiling.forLevel(level), canonical, viewerX, viewerZ);
    }

    public static AABB virtualAabb(DimensionTiling tiling, AABB canonical, double viewerX, double viewerZ) {
        double centerX = (canonical.minX + canonical.maxX) * 0.5;
        double centerZ = (canonical.minZ + canonical.maxZ) * 0.5;
        double dx = virtualBlock(tiling, centerX, viewerX) - centerX;
        double dz = virtualBlock(tiling, centerZ, viewerZ) - centerZ;
        if (dx == 0.0 && dz == 0.0) {
            return canonical;
        }
        return canonical.move(dx, 0.0, dz);
    }

    public static AABB wrapAabb(Level level, AABB box) {
        return wrapAabb(DimensionTiling.forLevel(level), box);
    }

    public static AABB wrapAabb(DimensionTiling tiling, AABB box) {
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        double dx = wrapBlock(tiling, centerX) - centerX;
        double dz = wrapBlock(tiling, centerZ) - centerZ;
        if (dx == 0.0 && dz == 0.0) {
            return box;
        }
        return box.move(dx, 0.0, dz);
    }

    /** Returns the virtual tile of canonical coord nearest to playerCoord. */
    public static int virtualChunk(int canonical, int playerChunk) {
        return virtualChunk(DimensionTiling.currentOrOverworld(), canonical, playerChunk);
    }

    public static int virtualChunk(Level level, int canonical, int playerChunk) {
        return virtualChunk(DimensionTiling.forLevel(level), canonical, playerChunk);
    }

    public static int virtualChunk(DimensionTiling tiling, int canonical, int playerChunk) {
        if (!tiling.enabled()) {
            return canonical;
        }
        int tileSize = tiling.tileSizeChunks();
        int k = Math.floorDiv(playerChunk - canonical + tileSize / 2, tileSize);
        return canonical + k * tileSize;
    }

    public static int wrappedChunkDistance(DimensionTiling tiling, int a, int b) {
        if (!tiling.enabled()) {
            return Math.abs(a - b);
        }

        int tileSize = tiling.tileSizeChunks();
        int wrappedDelta = Math.floorMod(a - b, tileSize);
        return Math.min(wrappedDelta, tileSize - wrappedDelta);
    }

    private static double positiveModulo(double value, double modulus) {
        return value - Math.floor(value / modulus) * modulus;
    }
}
