package globe.world.util;

import globe.world.config.TilingMode;
import globe.world.topology.TileGeometry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobeEntityAliasingTest {
    @Test
    void unlimitedHexAliasesMatchBruteForceInNearCancellingDirections() {
        TileGeometry geometry = TileGeometry.create(
                new DimensionTiling(TilingMode.HEX, true, 8, TerrainMode.EDGE_BLEND));
        AABB source = AABB.ofSize(Vec3.ZERO, 1.0D, 1.0D, 1.0D);
        double renderRadius = 960.0D;

        List<GlobeEntityAliasing.AliasOffset> actual = GlobeEntityAliasing.visualLatticeOffsets(
                geometry,
                source,
                source,
                Vec3.ZERO,
                renderRadius,
                Math.sqrt(0.5D),
                Integer.MAX_VALUE);

        assertEquals(bruteForceOffsets(geometry, source, Vec3.ZERO, renderRadius, 20), coordinates(actual));
        assertTrue(coordinates(actual).contains("10,-5"));
        assertEquals(actual.size(), coordinates(actual).size());
    }

    @Test
    void finiteRingCapIntentionallyLimitsNearCancellingAliases() {
        TileGeometry geometry = TileGeometry.create(
                new DimensionTiling(TilingMode.HEX, true, 8, TerrainMode.EDGE_BLEND));
        AABB source = AABB.ofSize(Vec3.ZERO, 1.0D, 1.0D, 1.0D);

        Set<String> offsets = coordinates(GlobeEntityAliasing.visualLatticeOffsets(
                geometry,
                source,
                source,
                Vec3.ZERO,
                960.0D,
                Math.sqrt(0.5D),
                9));

        assertFalse(offsets.contains("10,-5"));
    }

    @Test
    void unlimitedOffsetSquareAliasesMatchBruteForceCancellation() {
        TileGeometry geometry = TileGeometry.create(
                new DimensionTiling(TilingMode.OFFSET_SQUARE, true, 2, TerrainMode.EDGE_BLEND));
        AABB source = AABB.ofSize(Vec3.ZERO, 1.0D, 1.0D, 1.0D);
        double renderRadius = 320.0D;

        List<GlobeEntityAliasing.AliasOffset> actual = GlobeEntityAliasing.visualLatticeOffsets(
                geometry,
                source,
                source,
                Vec3.ZERO,
                renderRadius,
                Math.sqrt(0.5D),
                Integer.MAX_VALUE);

        assertEquals(bruteForceOffsets(geometry, source, Vec3.ZERO, renderRadius, 20), coordinates(actual));
        assertTrue(coordinates(actual).contains("10,-5"));
        assertEquals(actual.size(), coordinates(actual).size());
    }

    private static Set<String> bruteForceOffsets(
            TileGeometry geometry,
            AABB source,
            Vec3 camera,
            double renderRadius,
            int coordinateRadius) {
        Set<String> offsets = new HashSet<>();
        double renderRadiusSqr = renderRadius * renderRadius;
        for (int k = -coordinateRadius; k <= coordinateRadius; k++) {
            for (int l = -coordinateRadius; l <= coordinateRadius; l++) {
                if (k == 0 && l == 0) {
                    continue;
                }
                ChunkPos translation = geometry.latticeTranslation(new TileGeometry.LatticeCoordinate(k, l));
                AABB alias = source.move(translation.x() * 16.0D, 0.0D, translation.z() * 16.0D);
                if (GlobeEntityAliasing.distanceToBoxSqr(camera, alias) <= renderRadiusSqr) {
                    offsets.add(k + "," + l);
                }
            }
        }
        return offsets;
    }

    private static Set<String> coordinates(List<GlobeEntityAliasing.AliasOffset> offsets) {
        return offsets.stream()
                .map(offset -> offset.tileX() + "," + offset.tileZ())
                .collect(java.util.stream.Collectors.toSet());
    }
}
