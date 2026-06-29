package globe.world.util;

import globe.world.topology.TileGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

public record PeriodicPositionalRandomFactory(
        PositionalRandomFactory delegate,
        DimensionTiling tiling,
        CoordinateUnit coordinateUnit) implements PositionalRandomFactory {
    public enum CoordinateUnit {
        BLOCK,
        CHUNK
    }

    public static PositionalRandomFactory block(PositionalRandomFactory delegate) {
        return wrap(delegate, CoordinateUnit.BLOCK);
    }

    public static PositionalRandomFactory chunk(PositionalRandomFactory delegate) {
        return wrap(delegate, CoordinateUnit.CHUNK);
    }

    private static PositionalRandomFactory wrap(PositionalRandomFactory delegate, CoordinateUnit coordinateUnit) {
        DimensionTiling tiling = DimensionTiling.currentOrOverworld();
        if (!tiling.enabled()) {
            return delegate;
        }
        if (delegate instanceof PeriodicPositionalRandomFactory periodic) {
            if (periodic.tiling().equals(tiling) && periodic.coordinateUnit() == coordinateUnit) {
                return periodic;
            }
            delegate = periodic.delegate();
        }
        return new PeriodicPositionalRandomFactory(delegate, tiling, coordinateUnit);
    }

    @Override
    public RandomSource at(int x, int y, int z) {
        TileGeometry geometry = TileGeometry.create(this.tiling);
        if (this.coordinateUnit == CoordinateUnit.BLOCK) {
            BlockPos canonical = geometry.canonicalBlock(x, y, z);
            return this.delegate.at(canonical.getX(), y, canonical.getZ());
        }
        ChunkPos canonical = geometry.canonicalChunk(x, z);
        return this.delegate.at(canonical.x(), y, canonical.z());
    }

    @Override
    public RandomSource fromHashOf(String name) {
        return this.delegate.fromHashOf(name);
    }

    @Override
    public RandomSource fromHashOf(Identifier name) {
        return this.delegate.fromHashOf(name);
    }

    @Override
    public RandomSource fromSeed(long seed) {
        return this.delegate.fromSeed(seed);
    }

    @Override
    public void parityConfigString(StringBuilder sb) {
        this.delegate.parityConfigString(sb);
        sb.append(", globeWorldGeometry: ")
                .append(TileGeometry.create(this.tiling).geometryRevision())
                .append(", globeWorldCoordinateUnit: ")
                .append(this.coordinateUnit);
    }
}
