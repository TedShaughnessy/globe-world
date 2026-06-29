package globe.world.topology;

import globe.world.config.TilingMode;
import globe.world.util.DimensionTiling;
import globe.world.util.TerrainMode;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileGeometryTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void squareGeometryMatchesCurrentWrapping() {
        TileGeometry geometry = TileGeometry.create(new DimensionTiling(TilingMode.SQUARE, true, 16, TerrainMode.COMPACT_TORUS));

        assertEquals(new ChunkPos(-8, 7), geometry.canonicalChunk(8, -9));
        assertEquals(new BlockPos(-128, 64, 127), geometry.canonicalBlock(128, 64, -129));
        assertEquals(new ChunkPos(24, -25), geometry.nearestAlias(new ChunkPos(-8, 7), new ChunkPos(20, -20)));
        assertEquals(4.0D, geometry.wrappedDistanceSqr(new Vec3(127, 0, 0), new Vec3(-127, 0, 0)));
    }

    @Test
    void equivalentTilingSettingsReuseGeometry() {
        DimensionTiling tiling = new DimensionTiling(TilingMode.HEX, true, 8, TerrainMode.EDGE_BLEND);

        assertSame(TileGeometry.create(tiling), TileGeometry.create(tiling));
        assertSame(
                TileGeometry.create(tiling),
                TileGeometry.create(new DimensionTiling(TilingMode.HEX, true, 8, TerrainMode.EDGE_BLEND))
        );
    }

    @Test
    void sampledRawChunksMapIntoHexMaskAndCanonicalChunksStayPut() {
        HexTileGeometry geometry = hex(16);
        int rangeX = geometry.widthChunks() * 3;
        int rangeZ = geometry.heightChunks() * 3;

        for (int x = -rangeX; x <= rangeX; x++) {
            for (int z = -rangeZ; z <= rangeZ; z++) {
                ChunkPos canonical = geometry.canonicalChunk(x, z);
                assertTrue(geometry.isCanonicalChunk(canonical), "raw " + x + "," + z + " -> " + canonical);
                if (geometry.isCanonicalChunk(new ChunkPos(x, z))) {
                    assertEquals(new ChunkPos(x, z), canonical);
                }
            }
        }
    }

    @Test
    void minimumHexHasExpectedMaskAndLattice() {
        HexTileGeometry geometry = hex(8);
        List<Integer> rowWidths = new java.util.ArrayList<>();
        int canonicalChunks = 0;
        for (int z = -5; z <= 4; z++) {
            int rowWidth = 0;
            for (int x = -4; x <= 3; x++) {
                if (geometry.isCanonicalChunk(new ChunkPos(x, z))) {
                    rowWidth++;
                    canonicalChunks++;
                }
            }
            rowWidths.add(rowWidth);
        }

        assertEquals(8, geometry.widthChunks());
        assertEquals(8, geometry.heightChunks());
        assertEquals(64, canonicalChunks);
        assertEquals(List.of(2, 6, 8, 8, 8, 8, 8, 8, 6, 2), rowWidths);
        assertEquals(new ChunkPos(8, 0), geometry.latticeA());
        assertEquals(new ChunkPos(4, 8), geometry.latticeB());
        assertEquals(new ChunkPos(4, -8), geometry.latticeC());
    }

    @Test
    void hexLatticeAliasesMapBackToSameCanonicalOwner() {
        HexTileGeometry geometry = hex(16);
        ChunkPos canonical = new ChunkPos(0, 0);
        List<ChunkPos> edgeTranslations = List.of(
                geometry.latticeA(),
                new ChunkPos(-geometry.latticeA().x(), -geometry.latticeA().z()),
                geometry.latticeB(),
                new ChunkPos(-geometry.latticeB().x(), -geometry.latticeB().z()),
                geometry.latticeC(),
                new ChunkPos(-geometry.latticeC().x(), -geometry.latticeC().z())
        );

        assertTrue(geometry.isCanonicalChunk(canonical));
        for (ChunkPos translation : edgeTranslations) {
            ChunkPos rawAlias = new ChunkPos(canonical.x() + translation.x(), canonical.z() + translation.z());
            assertEquals(canonical, geometry.canonicalChunk(rawAlias.x(), rawAlias.z()));
        }
    }

    @Test
    void hexBlockCanonicalizationPreservesBlockLocalCoordinates() {
        HexTileGeometry geometry = hex(16);
        ChunkPos translation = geometry.latticeB();
        BlockPos raw = new BlockPos(translation.x() * 16 + 7, 80, translation.z() * 16 + 11);

        assertEquals(new BlockPos(7, 80, 11), geometry.canonicalBlock(raw.getX(), raw.getY(), raw.getZ()));
    }

    @Test
    void hexNearestAliasSelectsExpectedNeighboringCopies() {
        HexTileGeometry geometry = hex(16);
        ChunkPos canonical = new ChunkPos(0, 0);

        assertEquals(geometry.latticeA(), delta(canonical, geometry.nearestAlias(canonical, geometry.latticeA())));
        assertEquals(geometry.latticeB(), delta(canonical, geometry.nearestAlias(canonical, geometry.latticeB())));
        assertEquals(geometry.latticeC(), delta(canonical, geometry.nearestAlias(canonical, geometry.latticeC())));
    }

    @Test
    void hexWrappedDistanceIsSymmetric() {
        HexTileGeometry geometry = hex(16);
        Vec3 a = new Vec3(3.25D, 70.0D, 6.75D);
        Vec3 b = new Vec3(geometry.latticeB().x() * 16.0D + 4.25D, 73.0D, geometry.latticeB().z() * 16.0D + 8.75D);

        assertEquals(geometry.wrappedDistanceSqr(a, b), geometry.wrappedDistanceSqr(b, a));
        assertEquals(14.0D, geometry.wrappedDistanceSqr(a, b));
    }

    @Test
    void hexCanonicalQueryBoxesCoverAliasesNearAllLatticeEdges() {
        HexTileGeometry geometry = hex(16);
        Vec3 canonicalCenter = new Vec3(8.0D, 64.0D, 8.0D);
        for (ChunkPos translation : List.of(
                geometry.latticeA(),
                geometry.latticeB(),
                geometry.latticeC(),
                new ChunkPos(-geometry.latticeA().x(), -geometry.latticeA().z()),
                new ChunkPos(-geometry.latticeB().x(), -geometry.latticeB().z()),
                new ChunkPos(-geometry.latticeC().x(), -geometry.latticeC().z()))) {
            Vec3 visibleCenter = canonicalCenter.add(translation.x() * 16.0D, 0.0D, translation.z() * 16.0D);
            AABB visibleBox = AABB.ofSize(visibleCenter, 3.0D, 3.0D, 3.0D);

            boolean covered = geometry.canonicalQueryBoxes(visibleBox)
                    .stream()
                    .anyMatch(box -> box.inflate(1.0E-7D).contains(canonicalCenter));
            assertTrue(covered, "query boxes missed alias " + translation);
        }
    }

    @Test
    void squareAndHexExposeExactLatticeCoordinates() {
        TileGeometry square = TileGeometry.create(
                new DimensionTiling(TilingMode.SQUARE, true, 16, TerrainMode.COMPACT_TORUS));
        HexTileGeometry geometry = hex(16);

        assertEquals(
                new TileGeometry.LatticeCoordinate(2, -3),
                square.latticeCoordinate(new ChunkPos(32, -48)));

        for (TileGeometry.LatticeCoordinate coordinate : geometry.neighboringTiles()) {
            ChunkPos translation = geometry.latticeTranslation(coordinate);
            assertEquals(coordinate, geometry.latticeCoordinate(translation));
            assertEquals(
                    new ChunkPos(0, 0),
                    geometry.canonicalChunk(translation.x(), translation.z()));
        }
    }

    @Test
    void hexBoundarySegmentsMatchChunkMaskAndCoverSixSeams() {
        HexTileGeometry geometry = hex(16);
        Set<String> seamLabels = geometry.boundarySegments().stream()
                .map(segment -> segment.outsideAlias().seamLabel())
                .collect(Collectors.toSet());

        assertEquals(Set.of("+A", "-A", "+B", "-B", "+(A-B)", "-(A-B)"), seamLabels);
        for (TileGeometry.BoundarySegment segment : geometry.boundarySegments()) {
            Vec3 inside = segment.insidePoint(64.0D, 0.0D);
            Vec3 outside = new Vec3(
                    segment.midpointX() + segment.outward().getStepX() * 0.5D,
                    64.0D,
                    segment.midpointZ() + segment.outward().getStepZ() * 0.5D);
            BlockPos insideBlock = BlockPos.containing(inside);
            BlockPos outsideBlock = BlockPos.containing(outside);

            assertTrue(geometry.isCanonicalBlock(insideBlock), "inside " + segment);
            assertFalse(geometry.isCanonicalBlock(outsideBlock), "outside " + segment);
        }
    }

    private static HexTileGeometry hex(int tileSizeChunks) {
        return new HexTileGeometry(new DimensionTiling(TilingMode.HEX, true, tileSizeChunks, TerrainMode.EDGE_BLEND));
    }

    private static ChunkPos delta(ChunkPos from, ChunkPos to) {
        return new ChunkPos(to.x() - from.x(), to.z() - from.z());
    }
}
