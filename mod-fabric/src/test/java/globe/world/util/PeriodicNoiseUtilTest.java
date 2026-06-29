package globe.world.util;

import globe.world.config.TilingMode;
import globe.world.topology.HexTileGeometry;
import globe.world.topology.LatticeBlendGeometry;
import globe.world.topology.OffsetSquareTileGeometry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodicNoiseUtilTest {
    private static final double TAU = Math.PI * 2.0D;

    @Test
    void continuousHexGeometryUsesStraightHalfPlanesAndSharedWidth() {
        HexTileGeometry tile = hex(32);
        LatticeBlendGeometry blend = tile.blendGeometry().orElseThrow();
        ChunkPos a = tile.latticeA();
        ChunkPos b = tile.latticeB();
        double ax = a.x() * 16.0D;
        double az = a.z() * 16.0D;
        double bz = b.z() * 16.0D;

        assertTrue(blend.inradius() > blend.blendWidth());
        assertEquals(0.0D, blend.signedDistance(ax * 0.5D, az * 0.5D, 0, 0), 1.0E-12D);
        assertEquals(0.0D, blend.signedDistance(0.0D, bz * 0.5D, 0, 0), 1.0E-12D);
        assertTrue(blend.signedDistance(0.0D, 0.0D, 0, 0) > 0.0D);
    }

    @Test
    void hexBlendIsOneSampleInInteriorTwoOnSideAndThreeAtVertex() {
        HexTileGeometry tile = hex(32);
        LatticeBlendGeometry blend = tile.blendGeometry().orElseThrow();
        double ax = tile.latticeA().x() * 16.0D;
        double az = tile.latticeA().z() * 16.0D;
        double bz = tile.latticeB().z() * 16.0D;
        double vertexZ = bz * 0.5D;
        double vertexX = ((ax * ax + az * az) * 0.5D - vertexZ * az) / ax;

        assertEquals(1, contributorCount(tile.tiling(), 0.0D, 0.0D));
        assertEquals(2, contributorCount(tile.tiling(), 0.0D, vertexZ));
        assertEquals(3, contributorCount(tile.tiling(), vertexX, vertexZ));
        assertTrue(blend.weight(0.0D, 0.0D, 0, 0) == 1.0D);
    }

    @Test
    void blendedValueIsInvariantUnderAllHexLatticeTranslations() {
        HexTileGeometry tile = hex(32);
        double x = 37.0D;
        double z = -51.0D;
        PeriodicNoiseUtil.HorizontalSampler sampler =
                (translatedX, translatedZ) -> translatedX * 0.125D + translatedZ * translatedZ * 0.0003D;
        double expected = sample(tile.tiling(), x, z, sampler);

        for (ChunkPos translation : new ChunkPos[]{
                tile.latticeA(),
                tile.latticeB(),
                tile.latticeC(),
                new ChunkPos(-tile.latticeA().x(), -tile.latticeA().z()),
                new ChunkPos(-tile.latticeB().x(), -tile.latticeB().z()),
                new ChunkPos(-tile.latticeC().x(), -tile.latticeC().z())}) {
            assertEquals(
                    expected,
                    sample(
                            tile.tiling(),
                            x + translation.x() * 16.0D,
                            z + translation.z() * 16.0D,
                            sampler),
                    1.0E-12D);
        }
    }

    @Test
    void minimumHexOverlappingBandsStayFiniteAndInvariant() {
        HexTileGeometry tile = hex(8);
        PeriodicNoiseUtil.HorizontalSampler sampler =
                (translatedX, translatedZ) -> Math.sin(translatedX * 0.01D) + Math.cos(translatedZ * 0.02D);

        for (int x = -128; x <= 128; x += 17) {
            for (int z = -128; z <= 128; z += 19) {
                double value = sample(tile.tiling(), x, z, sampler);
                assertTrue(Double.isFinite(value));
                assertEquals(
                        value,
                        sample(
                                tile.tiling(),
                                x + tile.latticeA().x() * 16.0D,
                                z + tile.latticeA().z() * 16.0D,
                                sampler),
                        1.0E-11D);
            }
        }
    }

    @Test
    void horizontalTranslationHappensBeforeScalingAndInputReordering() {
        HexTileGeometry tile = hex(32);
        double scale = 0.25D;
        PeriodicNoiseUtil.HorizontalSampler reordered =
                (translatedX, translatedZ) -> translatedZ * scale + translatedX * scale * 10.0D;
        double expected = sample(tile.tiling(), 27.0D, -39.0D, reordered);

        assertEquals(
                expected,
                sample(
                        tile.tiling(),
                        27.0D + tile.latticeA().x() * 16.0D,
                        -39.0D + tile.latticeA().z() * 16.0D,
                        reordered),
                1.0E-12D);
    }

    @Test
    void positionalFactoriesCanonicalizeCompleteHexPairsInTheirCoordinateUnit() {
        HexTileGeometry tile = hex(32);
        PositionalRandomFactory base = RandomSource.create(12345L).forkPositional();
        PositionalRandomFactory blocks = DimensionTiling.with(
                tile.tiling(),
                () -> PeriodicPositionalRandomFactory.block(base));
        PositionalRandomFactory chunks = DimensionTiling.with(
                tile.tiling(),
                () -> PeriodicPositionalRandomFactory.chunk(base));
        ChunkPos a = tile.latticeA();

        assertEquals(
                blocks.at(7, 80, 11).nextLong(),
                blocks.at(7 + a.x() * 16, 80, 11 + a.z() * 16).nextLong());
        assertEquals(
                chunks.at(3, 0, -2).nextLong(),
                chunks.at(3 + a.x(), 0, -2 + a.z()).nextLong());
    }

    @Test
    void offsetSquareBlendIsFiniteAndInvariantUnderAllSixSeamTranslations() {
        for (int width : new int[]{2, 8, 16, 32}) {
            OffsetSquareTileGeometry tile = offsetSquare(width);
            PeriodicNoiseUtil.HorizontalSampler sampler =
                    (translatedX, translatedZ) ->
                            Math.sin(translatedX * 0.013D) + Math.cos(translatedZ * 0.019D);
            double x = 21.25D;
            double z = -37.75D;
            double expected = sample(tile.tiling(), x, z, sampler);

            assertTrue(Double.isFinite(expected));
            for (globe.world.topology.TileGeometry.LatticeCoordinate coordinate : tile.neighboringTiles()) {
                ChunkPos translation = tile.latticeTranslation(coordinate);
                assertEquals(
                        expected,
                        sample(
                                tile.tiling(),
                                x + translation.x() * 16.0D,
                                z + translation.z() * 16.0D,
                                sampler),
                        1.0E-11D);
            }
        }
    }

    @Test
    void offsetSquarePositionalFactoriesCanonicalizeCoupledPairs() {
        OffsetSquareTileGeometry tile = offsetSquare(16);
        PositionalRandomFactory base = RandomSource.create(54321L).forkPositional();
        PositionalRandomFactory blocks = DimensionTiling.with(
                tile.tiling(),
                () -> PeriodicPositionalRandomFactory.block(base));
        PositionalRandomFactory chunks = DimensionTiling.with(
                tile.tiling(),
                () -> PeriodicPositionalRandomFactory.chunk(base));

        for (globe.world.topology.TileGeometry.LatticeCoordinate coordinate : tile.neighboringTiles()) {
            ChunkPos translation = tile.latticeTranslation(coordinate);
            assertEquals(
                    blocks.at(7, 80, 11).nextLong(),
                    blocks.at(7 + translation.x() * 16, 80, 11 + translation.z() * 16).nextLong());
            assertEquals(
                    chunks.at(3, 0, -2).nextLong(),
                    chunks.at(3 + translation.x(), 0, -2 + translation.z()).nextLong());
        }
    }

    @Test
    void squareEdgeBlendAndCompactTorusSamplingRemainUnchanged() {
        PeriodicNoiseUtil.PlaneSampler sampler = (first, second) ->
                first * 0.17D + second * second * 0.003D;
        DimensionTiling edge = new DimensionTiling(
                TilingMode.SQUARE,
                true,
                64,
                TerrainMode.EDGE_BLEND);
        DimensionTiling compact = new DimensionTiling(
                TilingMode.SQUARE,
                true,
                8,
                TerrainMode.COMPACT_TORUS);

        for (int[] point : new int[][]{{0, 0}, {500, -481}, {-512, 511}, {1_037, -2_049}}) {
            assertEquals(
                    legacySquareEdge(point[0], point[1], edge.tileSizeBlocks(), 0.37D, sampler),
                    DimensionTiling.with(edge, () -> PeriodicNoiseUtil.samplePlane(
                            point[0],
                            point[1],
                            0.37D,
                            sampler)),
                    0.0D);
            assertEquals(
                    legacySquareCompact(point[0], point[1], compact.tileSizeBlocks(), -0.2D, sampler),
                    DimensionTiling.with(compact, () -> PeriodicNoiseUtil.samplePlane(
                            point[0],
                            point[1],
                            -0.2D,
                            sampler)),
                    0.0D);
        }
    }

    private static int contributorCount(DimensionTiling tiling, double x, double z) {
        AtomicInteger calls = new AtomicInteger();
        DimensionTiling.with(tiling, () -> PeriodicNoiseUtil.sampleHorizontal(
                x,
                z,
                (translatedX, translatedZ) -> calls.incrementAndGet()));
        return calls.get();
    }

    private static double sample(
            DimensionTiling tiling,
            double x,
            double z,
            PeriodicNoiseUtil.HorizontalSampler sampler) {
        return DimensionTiling.with(tiling, () -> PeriodicNoiseUtil.sampleHorizontal(x, z, sampler));
    }

    private static HexTileGeometry hex(int tileSizeChunks) {
        return new HexTileGeometry(
                new DimensionTiling(TilingMode.HEX, true, tileSizeChunks, TerrainMode.EDGE_BLEND));
    }

    private static OffsetSquareTileGeometry offsetSquare(int tileSizeChunks) {
        return new OffsetSquareTileGeometry(
                new DimensionTiling(TilingMode.OFFSET_SQUARE, true, tileSizeChunks, TerrainMode.EDGE_BLEND));
    }

    private static double legacySquareEdge(
            int x,
            int z,
            int period,
            double scale,
            PeriodicNoiseUtil.PlaneSampler sampler) {
        LegacyAxis first = legacyAxis(x, period, scale);
        LegacyAxis second = legacyAxis(z, period, scale);
        double value = sampler.sample(first.base(), second.base())
                * (1.0D - first.weight()) * (1.0D - second.weight());
        if (first.weight() > 0.0D) {
            value += sampler.sample(first.copy(), second.base())
                    * first.weight() * (1.0D - second.weight());
        }
        if (second.weight() > 0.0D) {
            value += sampler.sample(first.base(), second.copy())
                    * (1.0D - first.weight()) * second.weight();
        }
        if (first.weight() > 0.0D && second.weight() > 0.0D) {
            value += sampler.sample(first.copy(), second.copy()) * first.weight() * second.weight();
        }
        return value;
    }

    private static LegacyAxis legacyAxis(int coordinate, int period, double scale) {
        int half = period / 2;
        int canonical = Math.floorMod(coordinate + half, period) - half;
        int local = canonical + half;
        int band = Math.clamp(period / 8, 64, 256);
        double weight = 0.0D;
        int copyOffset = 0;
        if (local < band) {
            weight = 0.5D * (1.0D - smoothstep((double) local / band));
            copyOffset = period;
        } else if (local >= period - band) {
            weight = 0.5D * smoothstep((double) (local - (period - band)) / band);
            copyOffset = -period;
        }
        return new LegacyAxis(canonical * scale, (canonical + copyOffset) * scale, weight);
    }

    private static double legacySquareCompact(
            int x,
            int z,
            int period,
            double scale,
            PeriodicNoiseUtil.PlaneSampler sampler) {
        double firstAngle = TAU * x / period;
        double secondAngle = TAU * z / period;
        double radius = period * Math.abs(scale) / TAU;
        double sign = Math.signum(scale);
        double sinFirst = sign * radius * Math.sin(firstAngle);
        double cosFirst = sign * radius * Math.cos(firstAngle);
        double sinSecond = sign * radius * Math.sin(secondAngle);
        double cosSecond = sign * radius * Math.cos(secondAngle);
        return (
                sampler.sample(sinFirst, sinSecond)
                        + sampler.sample(sinFirst + 37.719D, cosSecond - 11.137D)
                        + sampler.sample(cosFirst - 53.421D, sinSecond + 19.173D)
                        + sampler.sample(cosFirst + 101.311D, cosSecond + 47.619D)
        ) * 0.25D;
    }

    private static double smoothstep(double value) {
        double clamped = Math.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private record LegacyAxis(double base, double copy, double weight) {
    }
}
