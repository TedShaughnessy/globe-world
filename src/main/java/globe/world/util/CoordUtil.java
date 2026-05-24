package globe.world.util;

import globe.world.config.GlobeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CoordUtil {
    public static int wrapChunk(int c) {
        if (!GlobeConfig.enabled()) {
            return c;
        }
        int tileSize = GlobeConfig.tileSizeChunks();
        int half = tileSize / 2;
        return Math.floorMod(c + half, tileSize) - half;
    }

    public static int wrapBlock(int b) {
        if (!GlobeConfig.enabled()) {
            return b;
        }
        int tileSize = GlobeConfig.tileSizeBlocks();
        int half = tileSize / 2;
        return Math.floorMod(b + half, tileSize) - half;
    }

    public static BlockPos wrapBlockPos(BlockPos pos) {
        int x = wrapBlock(pos.getX());
        int z = wrapBlock(pos.getZ());
        if (x == pos.getX() && z == pos.getZ()) {
            return pos;
        }
        return new BlockPos(x, pos.getY(), z);
    }

    public static ChunkPos wrapChunkPos(ChunkPos pos) {
        int x = wrapChunk(pos.x());
        int z = wrapChunk(pos.z());
        if (x == pos.x() && z == pos.z()) {
            return pos;
        }
        return new ChunkPos(x, z);
    }

    public static int tileAliasChunk(int chunk) {
        if (!GlobeConfig.enabled()) {
            return 0;
        }
        return (chunk - wrapChunk(chunk)) / GlobeConfig.tileSizeChunks();
    }

    public static int tileAliasBlock(int block) {
        if (!GlobeConfig.enabled()) {
            return 0;
        }
        return (block - wrapBlock(block)) / GlobeConfig.tileSizeBlocks();
    }

    public static boolean isInCanonicalTile(BlockPos pos) {
        return !GlobeConfig.enabled()
                || (pos.getX() == wrapBlock(pos.getX()) && pos.getZ() == wrapBlock(pos.getZ()));
    }

    public static double wrappedDeltaBlock(double a, double b) {
        if (!GlobeConfig.enabled()) {
            return a - b;
        }
        double d = a - b;
        int tileSize = GlobeConfig.tileSizeBlocks();
        return d - Math.rint(d / tileSize) * tileSize;
    }

    public static double wrappedDistanceSqrXZ(double ax, double az, double bx, double bz) {
        double dx = wrappedDeltaBlock(ax, bx);
        double dz = wrappedDeltaBlock(az, bz);
        return dx * dx + dz * dz;
    }

    public static double wrappedDistanceSqr(double ax, double ay, double az, double bx, double by, double bz) {
        double dy = ay - by;
        return wrappedDistanceSqrXZ(ax, az, bx, bz) + dy * dy;
    }

    public static double wrappedDistanceSqr(Entity a, Entity b) {
        return wrappedDistanceSqr(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    public static double wrappedChunkDistanceSqr(ChunkPos chunkPos, Vec3 pos) {
        return wrappedDistanceSqrXZ(chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ(), pos.x, pos.z);
    }

    public static double virtualBlock(double canonical, double viewer) {
        if (!GlobeConfig.enabled()) {
            return canonical;
        }
        int tileSize = GlobeConfig.tileSizeBlocks();
        return canonical + Math.rint((viewer - canonical) / tileSize) * tileSize;
    }

    public static AABB virtualAabb(AABB canonical, double viewerX, double viewerZ) {
        double centerX = (canonical.minX + canonical.maxX) * 0.5;
        double centerZ = (canonical.minZ + canonical.maxZ) * 0.5;
        double dx = virtualBlock(centerX, viewerX) - centerX;
        double dz = virtualBlock(centerZ, viewerZ) - centerZ;
        if (dx == 0.0 && dz == 0.0) {
            return canonical;
        }
        return canonical.move(dx, 0.0, dz);
    }

    /** Returns the virtual tile of canonical coord nearest to playerCoord. */
    public static int virtualChunk(int canonical, int playerChunk) {
        if (!GlobeConfig.enabled()) {
            return canonical;
        }
        int tileSize = GlobeConfig.tileSizeChunks();
        int k = Math.floorDiv(playerChunk - canonical + tileSize / 2, tileSize);
        return canonical + k * tileSize;
    }
}
