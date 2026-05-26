package globe.world.util;

import globe.world.config.GlobeConfig;
import globe.world.mixin.ImprovedNoiseAccessor;
import globe.world.mixin.NormalNoiseAccessor;
import globe.world.mixin.PerlinNoiseAccessor;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;

public class PeriodicNoiseUtil {
    private static final double TAU = Math.PI * 2.0;
    private static final double NORMAL_NOISE_INPUT_FACTOR = 1.0181268882175227;
    private static final int EDGE_BLEND_MIN_TILE_CHUNKS = 64;
    private static final int PERIODIC_LATTICE_TILE_CHUNK_MULTIPLE = 256;
    private static final int MIN_EDGE_BLEND_BAND_BLOCKS = 64;
    private static final int MAX_EDGE_BLEND_BAND_BLOCKS = 256;
    private static final int[][] GRADIENT = new int[][]{
            {1, 1, 0},
            {-1, 1, 0},
            {1, -1, 0},
            {-1, -1, 0},
            {1, 0, 1},
            {-1, 0, 1},
            {1, 0, -1},
            {-1, 0, -1},
            {0, 1, 1},
            {0, -1, 1},
            {0, 1, -1},
            {0, -1, -1},
            {1, 1, 0},
            {0, -1, 1},
            {-1, 1, 0},
            {0, -1, -1}
    };

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

        TerrainMode mode = terrainMode();
        if (mode == TerrainMode.EDGE_BLEND || mode == TerrainMode.PERIODIC_LATTICE) {
            return sampleEdgeBlendedPlane(firstCoord, secondCoord, period, scale, sampler);
        }

        return sampleCompactTorusPlane(firstCoord, secondCoord, period, scale, sampler);
    }

    public static double sampleNoiseHolderXZ(
            int blockX,
            int blockZ,
            double scale,
            double y,
            DensityFunction.NoiseHolder holder) {
        return sampleNoiseHolderXZ(blockX, blockZ, scale, 0.0, y, 0.0, holder);
    }

    public static double sampleNoiseHolderXZ(
            int blockX,
            int blockZ,
            double scale,
            double offsetX,
            double y,
            double offsetZ,
            DensityFunction.NoiseHolder holder) {
        NormalNoise noise = holder.noise();
        if (noise == null) {
            return 0.0;
        }
        return sampleNormalNoiseXZ(blockX, blockZ, scale, offsetX, y, offsetZ, noise);
    }

    public static double sampleNoiseHolderXY(
            int blockX,
            int blockY,
            double scale,
            double z,
            DensityFunction.NoiseHolder holder) {
        NormalNoise noise = holder.noise();
        if (noise == null) {
            return 0.0;
        }

        if (!GlobeConfig.enabled() || terrainMode() != TerrainMode.PERIODIC_LATTICE || scale == 0.0) {
            return samplePlane(blockX, blockY, scale, (x, y) -> noise.getValue(x, y, z));
        }

        NoiseAxis x = NoiseAxis.periodic(blockX, scale, 0.0);
        NoiseAxis y = NoiseAxis.periodic(blockY, scale, 0.0);
        NoiseAxis fixedZ = NoiseAxis.fixed(z);
        return sampleNormalNoise(x, y, fixedZ, noise);
    }

    public static double sampleNormalNoiseXZ(int blockX, int blockZ, double scale, double y, NormalNoise noise) {
        return sampleNormalNoiseXZ(blockX, blockZ, scale, 0.0, y, 0.0, noise);
    }

    public static double sampleNormalNoiseXZ(
            int blockX,
            int blockZ,
            double scale,
            double offsetX,
            double y,
            double offsetZ,
            NormalNoise noise) {
        if (!GlobeConfig.enabled() || terrainMode() != TerrainMode.PERIODIC_LATTICE || scale == 0.0) {
            return samplePlane(blockX, blockZ, scale, (x, z) -> noise.getValue(x + offsetX, y, z + offsetZ));
        }

        NoiseAxis x = NoiseAxis.periodic(blockX, scale, offsetX);
        NoiseAxis fixedY = NoiseAxis.fixed(y);
        NoiseAxis z = NoiseAxis.periodic(blockZ, scale, offsetZ);
        return sampleNormalNoise(x, fixedY, z, noise);
    }

    public static double sampleImprovedNoiseXZ(
            int blockX,
            int blockZ,
            double scale,
            double y,
            double yScale,
            double yFudge,
            ImprovedNoise noise) {
        if (!GlobeConfig.enabled() || terrainMode() != TerrainMode.PERIODIC_LATTICE || scale == 0.0) {
            return samplePlane(
                    blockX,
                    blockZ,
                    scale,
                    (x, z) -> noise.noise(PerlinNoise.wrap(x), y, PerlinNoise.wrap(z), yScale, yFudge)
            );
        }

        NoiseAxis x = NoiseAxis.periodic(blockX, scale, 0.0);
        NoiseAxis fixedY = NoiseAxis.fixed(y);
        NoiseAxis z = NoiseAxis.periodic(blockZ, scale, 0.0);
        return sampleImprovedNoise(noise, x, fixedY, z, 1.0, yScale, yFudge);
    }

    private static double sampleNormalNoise(NoiseAxis x, NoiseAxis y, NoiseAxis z, NormalNoise noise) {
        NormalNoiseAccessor accessor = (NormalNoiseAccessor) noise;
        return (
                samplePerlinNoise(accessor.globeWorld$first(), x, y, z)
                        + samplePerlinNoise(
                        accessor.globeWorld$second(),
                        x.scaled(NORMAL_NOISE_INPUT_FACTOR),
                        y.scaled(NORMAL_NOISE_INPUT_FACTOR),
                        z.scaled(NORMAL_NOISE_INPUT_FACTOR)
                )
        ) * accessor.globeWorld$valueFactor();
    }

    private static double samplePerlinNoise(PerlinNoise noise, NoiseAxis x, NoiseAxis y, NoiseAxis z) {
        PerlinNoiseAccessor accessor = (PerlinNoiseAccessor) noise;
        ImprovedNoise[] noiseLevels = accessor.globeWorld$noiseLevels();
        DoubleList amplitudes = accessor.globeWorld$amplitudes();
        double value = 0.0;
        double factor = accessor.globeWorld$lowestFreqInputFactor();
        double valueFactor = accessor.globeWorld$lowestFreqValueFactor();

        for (int i = 0; i < noiseLevels.length; i++) {
            ImprovedNoise octave = noiseLevels[i];
            if (octave != null) {
                double noiseValue = sampleImprovedNoise(octave, x, y, z, factor, 0.0, 0.0);
                value += amplitudes.getDouble(i) * noiseValue * valueFactor;
            }

            factor *= 2.0;
            valueFactor /= 2.0;
        }

        return value;
    }

    private static double sampleImprovedNoise(
            ImprovedNoise noise,
            NoiseAxis x,
            NoiseAxis y,
            NoiseAxis z,
            double factor,
            double yScale,
            double yFudge) {
        int period = GlobeConfig.tileSizeBlocks();
        PeriodicSampleAxis sampleX = x.sample(factor, period);
        PeriodicSampleAxis sampleY = y.sample(factor, period);
        PeriodicSampleAxis sampleZ = z.sample(factor, period);
        return sampleImprovedNoise(
                noise,
                PerlinNoise.wrap(sampleX.value()),
                PerlinNoise.wrap(sampleY.value()),
                PerlinNoise.wrap(sampleZ.value()),
                yScale,
                yFudge,
                sampleX.periodCells(),
                sampleY.periodCells(),
                sampleZ.periodCells()
        );
    }

    private static double sampleImprovedNoise(
            ImprovedNoise noise,
            double inputX,
            double inputY,
            double inputZ,
            double yScale,
            double yFudge,
            int periodCellsX,
            int periodCellsY,
            int periodCellsZ) {
        double x = inputX + noise.xo;
        double y = inputY + noise.yo;
        double z = inputZ + noise.zo;
        int xf = Mth.floor(x);
        int yf = Mth.floor(y);
        int zf = Mth.floor(z);
        double xr = x - xf;
        double yr = y - yf;
        double zr = z - zf;
        double yrFudge;
        if (yScale != 0.0) {
            double fudgeLimit;
            if (yFudge >= 0.0 && yFudge < yr) {
                fudgeLimit = yFudge;
            } else {
                fudgeLimit = yr;
            }

            yrFudge = Mth.floor(fudgeLimit / yScale + 1.0E-7F) * yScale;
        } else {
            yrFudge = 0.0;
        }

        return sampleAndLerp(noise, xf, yf, zf, xr, yr - yrFudge, zr, yr, periodCellsX, periodCellsY, periodCellsZ);
    }

    private static double sampleAndLerp(
            ImprovedNoise noise,
            int x,
            int y,
            int z,
            double xr,
            double yr,
            double zr,
            double yrOriginal,
            int periodCellsX,
            int periodCellsY,
            int periodCellsZ) {
        int x0 = p(noise, wrapCell(x, periodCellsX));
        int x1 = p(noise, wrapCell(x + 1, periodCellsX));
        int y0 = wrapCell(y, periodCellsY);
        int y1 = wrapCell(y + 1, periodCellsY);
        int z0 = wrapCell(z, periodCellsZ);
        int z1 = wrapCell(z + 1, periodCellsZ);
        int xy00 = p(noise, x0 + y0);
        int xy01 = p(noise, x0 + y1);
        int xy10 = p(noise, x1 + y0);
        int xy11 = p(noise, x1 + y1);
        double d000 = gradDot(p(noise, xy00 + z0), xr, yr, zr);
        double d100 = gradDot(p(noise, xy10 + z0), xr - 1.0, yr, zr);
        double d010 = gradDot(p(noise, xy01 + z0), xr, yr - 1.0, zr);
        double d110 = gradDot(p(noise, xy11 + z0), xr - 1.0, yr - 1.0, zr);
        double d001 = gradDot(p(noise, xy00 + z1), xr, yr, zr - 1.0);
        double d101 = gradDot(p(noise, xy10 + z1), xr - 1.0, yr, zr - 1.0);
        double d011 = gradDot(p(noise, xy01 + z1), xr, yr - 1.0, zr - 1.0);
        double d111 = gradDot(p(noise, xy11 + z1), xr - 1.0, yr - 1.0, zr - 1.0);
        double xAlpha = Mth.smoothstep(xr);
        double yAlpha = Mth.smoothstep(yrOriginal);
        double zAlpha = Mth.smoothstep(zr);
        return Mth.lerp3(xAlpha, yAlpha, zAlpha, d000, d100, d010, d110, d001, d101, d011, d111);
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

    private static TerrainMode terrainMode() {
        int tileChunks = GlobeConfig.tileSizeChunks();
        if (tileChunks < EDGE_BLEND_MIN_TILE_CHUNKS) {
            return TerrainMode.COMPACT_TORUS;
        }
        if (tileChunks % PERIODIC_LATTICE_TILE_CHUNK_MULTIPLE == 0) {
            return TerrainMode.PERIODIC_LATTICE;
        }
        return TerrainMode.EDGE_BLEND;
    }

    private static int p(ImprovedNoise noise, int x) {
        return ((ImprovedNoiseAccessor) (Object) noise).globeWorld$permutations()[x & 0xFF] & 0xFF;
    }

    private static int wrapCell(int cell, int periodCells) {
        if (periodCells <= 0) {
            return cell;
        }
        return Math.floorMod(cell, periodCells);
    }

    private static double gradDot(int hash, double x, double y, double z) {
        int[] gradient = GRADIENT[hash & 15];
        return gradient[0] * x + gradient[1] * y + gradient[2] * z;
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

    private enum TerrainMode {
        COMPACT_TORUS,
        EDGE_BLEND,
        PERIODIC_LATTICE
    }

    private record NoiseAxis(boolean periodic, int blockCoord, double scale, double offset) {
        static NoiseAxis periodic(int blockCoord, double scale, double offset) {
            return new NoiseAxis(true, blockCoord, scale, offset);
        }

        static NoiseAxis fixed(double coordinate) {
            return new NoiseAxis(false, 0, 0.0, coordinate);
        }

        NoiseAxis scaled(double multiplier) {
            return new NoiseAxis(this.periodic, this.blockCoord, this.scale * multiplier, this.offset * multiplier);
        }

        PeriodicSampleAxis sample(double factor, int period) {
            if (!this.periodic) {
                return new PeriodicSampleAxis(this.offset * factor, 0);
            }
            PeriodicScale periodicScale = periodicScale(this.scale, factor, period);
            return new PeriodicSampleAxis(
                    this.blockCoord * periodicScale.scale() * factor + this.offset * factor,
                    periodicScale.periodCells()
            );
        }
    }

    private record PeriodicScale(double scale, int periodCells) {
    }

    private record PeriodicSampleAxis(double value, int periodCells) {
    }

    private static PeriodicScale periodicScale(double scale, double factor, int period) {
        if (scale == 0.0 || factor == 0.0) {
            return new PeriodicScale(0.0, 1);
        }

        int cells = Math.max(1, Math.toIntExact(Math.round(period * Math.abs(scale) * factor)));
        double adjustedScale = Math.copySign((double) cells / (period * factor), scale);
        return new PeriodicScale(adjustedScale, cells);
    }
}
