package globe.world.topology;

import globe.world.config.TilingMode;
import globe.world.util.DimensionTiling;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
}

final class TileGeometryCache {
    static final ConcurrentMap<DimensionTiling, TileGeometry> GEOMETRIES = new ConcurrentHashMap<>();

    private TileGeometryCache() {
    }
}
