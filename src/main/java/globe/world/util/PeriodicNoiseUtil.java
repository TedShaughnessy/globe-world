package globe.world.util;

import globe.world.config.GlobeConfig;

public class PeriodicNoiseUtil {
    private static final double TAU = Math.PI * 2.0;

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

        PeriodicPlane plane = mapPlane(firstCoord, secondCoord, period, scale);
        return (
                sampler.sample(plane.sinFirst(), plane.sinSecond())
                        + sampler.sample(plane.sinFirst() + 37.719, plane.cosSecond() - 11.137)
                        + sampler.sample(plane.cosFirst() - 53.421, plane.sinSecond() + 19.173)
                        + sampler.sample(plane.cosFirst() + 101.311, plane.cosSecond() + 47.619)
        ) * 0.25;
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
}
