package globe.world.util;

import globe.world.config.GlobeConfig;

public class PeriodicNoiseUtil {
    private static final double TAU = Math.PI * 2.0;
    private static final int EDGE_BLEND_MIN_TILE_CHUNKS = 64;
    private static final int MIN_EDGE_BLEND_BAND_BLOCKS = 64;
    private static final int MAX_EDGE_BLEND_BAND_BLOCKS = 256;

    @FunctionalInterface
    public interface PlaneSampler {
        double sample(double first, double second);
    }

    public static double samplePlane(int firstCoord, int secondCoord, double scale, PlaneSampler sampler) {
        if (!GlobeConfig.enabled()) {
            return sampler.sample(firstCoord * scale, secondCoord * scale);
        }

        int period = GlobeConfig.tileSizeBlocks();
        if (period <= 1 || scale == 0.0) {
            return sampler.sample(0.0, 0.0);
        }

        if (GlobeConfig.tileSizeChunks() >= EDGE_BLEND_MIN_TILE_CHUNKS) {
            return sampleEdgeBlendedPlane(firstCoord, secondCoord, period, scale, sampler);
        }

        return sampleCompactTorusPlane(firstCoord, secondCoord, period, scale, sampler);
    }

    private static double sampleCompactTorusPlane(int firstCoord, int secondCoord, int period, double scale, PlaneSampler sampler) {
        PeriodicPlane plane = mapPlane(firstCoord, secondCoord, period, scale);
        return (
                sampler.sample(plane.sinFirst(), plane.sinSecond())
                        + sampler.sample(plane.sinFirst() + 37.719, plane.cosSecond() - 11.137)
                        + sampler.sample(plane.cosFirst() - 53.421, plane.sinSecond() + 19.173)
                        + sampler.sample(plane.cosFirst() + 101.311, plane.cosSecond() + 47.619)
        ) * 0.25;
    }

    private static double sampleEdgeBlendedPlane(
            int firstCoord,
            int secondCoord,
            int period,
            double scale,
            PlaneSampler sampler) {
        EdgeBlendAxis first = edgeBlendAxis(firstCoord, period, scale);
        EdgeBlendAxis second = edgeBlendAxis(secondCoord, period, scale);
        double firstWeight = first.weight();
        double secondWeight = second.weight();

        double base = sampler.sample(first.base(), second.base());
        if (firstWeight == 0.0 && secondWeight == 0.0) {
            return base;
        }

        double value = base * (1.0 - firstWeight) * (1.0 - secondWeight);
        if (firstWeight > 0.0) {
            value += sampler.sample(first.copy(), second.base()) * firstWeight * (1.0 - secondWeight);
        }
        if (secondWeight > 0.0) {
            value += sampler.sample(first.base(), second.copy()) * (1.0 - firstWeight) * secondWeight;
        }
        if (firstWeight > 0.0 && secondWeight > 0.0) {
            value += sampler.sample(first.copy(), second.copy()) * firstWeight * secondWeight;
        }
        return value;
    }

    private static EdgeBlendAxis edgeBlendAxis(int coord, int period, double scale) {
        int canonical = wrapCoordinate(coord, period);
        int local = canonical + period / 2;
        int band = edgeBlendBand(period);
        double weight = 0.0;
        int copyOffset = 0;

        // Half-weight at the seam makes both sides converge to the same pair of vanilla samples.
        if (local < band) {
            weight = 0.5 * (1.0 - smoothstep((double) local / band));
            copyOffset = period;
        } else if (local >= period - band) {
            weight = 0.5 * smoothstep((double) (local - (period - band)) / band);
            copyOffset = -period;
        }

        return new EdgeBlendAxis(canonical * scale, (canonical + copyOffset) * scale, weight);
    }

    private static int wrapCoordinate(int coord, int period) {
        int half = period / 2;
        return Math.floorMod(coord + half, period) - half;
    }

    private static int edgeBlendBand(int period) {
        return Math.clamp(period / 8, MIN_EDGE_BLEND_BAND_BLOCKS, MAX_EDGE_BLEND_BAND_BLOCKS);
    }

    private static double smoothstep(double value) {
        double clamped = Math.clamp(value, 0.0, 1.0);
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static PeriodicPlane mapPlane(int firstCoord, int secondCoord, int period, double scale) {
        double firstAngle = TAU * firstCoord / period;
        double secondAngle = TAU * secondCoord / period;
        double radius = period * Math.abs(scale) / TAU;
        double sign = Math.signum(scale);

        return new PeriodicPlane(
                sign * radius * Math.sin(firstAngle),
                sign * radius * Math.cos(firstAngle),
                sign * radius * Math.sin(secondAngle),
                sign * radius * Math.cos(secondAngle)
        );
    }

    private record PeriodicPlane(double sinFirst, double cosFirst, double sinSecond, double cosSecond) {
    }

    private record EdgeBlendAxis(double base, double copy, double weight) {
    }
}
