package globe.world.util;

import globe.world.config.GlobeConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

public record PeriodicPositionalRandomFactory(PositionalRandomFactory delegate, int horizontalPeriod) implements PositionalRandomFactory {
    public static PositionalRandomFactory block(PositionalRandomFactory delegate) {
        return wrap(delegate, GlobeConfig.tileSizeBlocks());
    }

    public static PositionalRandomFactory chunk(PositionalRandomFactory delegate) {
        return wrap(delegate, GlobeConfig.tileSizeChunks());
    }

    private static PositionalRandomFactory wrap(PositionalRandomFactory delegate, int horizontalPeriod) {
        if (!GlobeConfig.enabled() || horizontalPeriod <= 1 || delegate instanceof PeriodicPositionalRandomFactory) {
            return delegate;
        }
        return new PeriodicPositionalRandomFactory(delegate, horizontalPeriod);
    }

    @Override
    public RandomSource at(int x, int y, int z) {
        return this.delegate.at(wrap(x), y, wrap(z));
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
        sb.append(", globeWorldHorizontalPeriod: ").append(this.horizontalPeriod);
    }

    private int wrap(int coord) {
        int half = this.horizontalPeriod / 2;
        return Math.floorMod(coord + half, this.horizontalPeriod) - half;
    }
}
