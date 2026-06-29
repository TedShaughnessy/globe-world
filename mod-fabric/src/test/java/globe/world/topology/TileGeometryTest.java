package globe.world.topology;

import globe.world.atlas.GlobeAtlasSurveyWindows;
import globe.world.config.TilingMode;
import globe.world.config.TopologySettings;
import globe.world.util.CoordUtil;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        assertEquals(256, geometry.longitudePeriodBlocks());
        assertEquals(-128.0D, geometry.canonicalLongitude(128.0D));
    }

    @Test
    void offsetSquareSanitizesWidthsAndOwnsExactSquare() {
        for (int configured : List.of(2, 7, 16, 31)) {
            OffsetSquareTileGeometry geometry = offsetSquare(configured);
            int width = geometry.tileSizeChunks();
            int half = width / 2;
            int canonicalChunks = 0;

            assertTrue(width >= 2);
            assertEquals(0, width % 2);
            assertEquals(configured % 2 == 0 ? configured : configured + 1, width);
            for (int x = -width * 2; x <= width * 2; x++) {
                for (int z = -width * 2; z <= width * 2; z++) {
                    ChunkPos canonical = geometry.canonicalChunk(x, z);
                    assertTrue(canonical.x() >= -half && canonical.x() < half);
                    assertTrue(canonical.z() >= -half && canonical.z() < half);
                }
            }
            for (int x = -half; x < half; x++) {
                for (int z = -half; z < half; z++) {
                    ChunkPos canonical = new ChunkPos(x, z);
                    assertTrue(geometry.isCanonicalChunk(canonical));
                    assertEquals(canonical, geometry.canonicalChunk(x, z));
                    canonicalChunks++;
                }
            }
            assertEquals(width * width, canonicalChunks);
            assertEquals(width * width, geometry.canonicalChunkCount());
        }
    }

    @Test
    void offsetSquareConfigurationForcesEdgeBlendAndKeepsNetherSquareOnly() {
        TopologySettings settings = TopologySettings.DEFAULT
                .withMode(TilingMode.OFFSET_SQUARE)
                .withTileSize(7)
                .withTerrainMode(TerrainMode.COMPACT_TORUS)
                .withNetherMode(TilingMode.OFFSET_SQUARE);
        DimensionTiling tiling = new DimensionTiling(
                settings.mode(),
                settings.enabled(),
                settings.tileSize(),
                settings.terrainMode());

        assertTrue(settings.enabled());
        assertFalse(settings.netherEnabled());
        assertTrue(settings.forceMissingStronghold());
        assertTrue(settings.avoidWaterOnlySeeds());
        assertEquals(8, settings.tileSize());
        assertEquals(TerrainMode.EDGE_BLEND, settings.terrainMode());
        assertEquals(TerrainMode.EDGE_BLEND, tiling.terrainMode());
    }

    @Test
    void offsetSquareUsesSpecifiedBasisAndExactLatticeCoordinates() {
        OffsetSquareTileGeometry geometry = offsetSquare(16);
        TileGeometry.LatticeBasis basis = geometry.latticeBasis();

        assertEquals("offset-square-north-south-v1", geometry.geometryRevision());
        assertEquals(new ChunkPos(16, 8), basis.a());
        assertEquals(new ChunkPos(0, 16), basis.b());
        for (TileGeometry.LatticeCoordinate coordinate : List.of(
                new TileGeometry.LatticeCoordinate(0, 0),
                new TileGeometry.LatticeCoordinate(1, 0),
                new TileGeometry.LatticeCoordinate(0, 1),
                new TileGeometry.LatticeCoordinate(1, -1),
                new TileGeometry.LatticeCoordinate(-3, 4),
                new TileGeometry.LatticeCoordinate(5, -7))) {
            ChunkPos translation = geometry.latticeTranslation(coordinate);
            assertEquals(coordinate, geometry.latticeCoordinate(translation));
            assertEquals(new ChunkPos(0, 0), geometry.canonicalChunk(translation.x(), translation.z()));
        }
    }

    @Test
    void offsetSquareCanonicalizationPreservesChunkAndBlockLocalCoordinates() {
        OffsetSquareTileGeometry geometry = offsetSquare(16);
        BlockPos canonical = new BlockPos(7, 80, 11);

        for (TileGeometry.LatticeCoordinate coordinate : geometry.neighboringTiles()) {
            ChunkPos translation = geometry.latticeTranslation(coordinate);
            BlockPos raw = canonical.offset(translation.x() * 16, 0, translation.z() * 16);
            assertEquals(canonical, geometry.canonicalBlock(raw.getX(), raw.getY(), raw.getZ()));

            Vec3 rawVector = new Vec3(raw.getX() + 0.375D, 80.625D, raw.getZ() + 0.875D);
            assertEquals(
                    new Vec3(canonical.getX() + 0.375D, 80.625D, canonical.getZ() + 0.875D),
                    geometry.canonicalBlock(rawVector));
        }
    }

    @Test
    void offsetSquareHalfOpenBoundariesChooseDeterministicOwners() {
        OffsetSquareTileGeometry geometry = offsetSquare(16);

        assertEquals(new ChunkPos(-8, -8), geometry.canonicalChunk(8, 0));
        assertEquals(new TileGeometry.LatticeCoordinate(1, 0), geometry.latticeCoordinate(new ChunkPos(8, 0)));
        assertEquals(new ChunkPos(-8, 7), geometry.canonicalChunk(8, -1));
        assertEquals(new TileGeometry.LatticeCoordinate(1, -1), geometry.latticeCoordinate(new ChunkPos(8, -1)));
        assertEquals(new ChunkPos(7, -8), geometry.canonicalChunk(-9, 0));
        assertEquals(new TileGeometry.LatticeCoordinate(-1, 1), geometry.latticeCoordinate(new ChunkPos(-9, 0)));
        assertEquals(new ChunkPos(7, 7), geometry.canonicalChunk(-9, -1));
        assertEquals(new TileGeometry.LatticeCoordinate(-1, 0), geometry.latticeCoordinate(new ChunkPos(-9, -1)));
    }

    @Test
    void offsetSquareNearestAliasesAndWrappedDistanceUseCompletePositions() {
        OffsetSquareTileGeometry geometry = offsetSquare(16);
        ChunkPos canonical = new ChunkPos(0, 0);
        for (TileGeometry.LatticeCoordinate coordinate : geometry.neighboringTiles()) {
            ChunkPos translation = geometry.latticeTranslation(coordinate);
            assertEquals(translation, geometry.nearestAlias(canonical, translation));
        }

        Vec3 a = new Vec3(3.25D, 70.0D, 6.75D);
        ChunkPos translation = geometry.latticeTranslation(new TileGeometry.LatticeCoordinate(1, -1));
        Vec3 b = new Vec3(
                translation.x() * 16.0D + 4.25D,
                73.0D,
                translation.z() * 16.0D + 8.75D);
        assertEquals(14.0D, geometry.wrappedDistanceSqr(a, b));
        assertEquals(geometry.wrappedDistanceSqr(a, b), geometry.wrappedDistanceSqr(b, a));
    }

    @Test
    void offsetSquareBoundarySegmentsDescribeAllSixNeighbors() {
        OffsetSquareTileGeometry geometry = offsetSquare(16);
        Set<String> seamLabels = geometry.boundarySegments().stream()
                .map(segment -> segment.outsideAlias().seamLabel())
                .collect(Collectors.toSet());

        assertEquals(6, geometry.boundarySegments().size());
        assertEquals(Set.of("+A", "-A", "+B", "-B", "+(A-B)", "-(A-B)"), seamLabels);
        for (TileGeometry.BoundarySegment segment : geometry.boundarySegments()) {
            Vec3 inside = segment.insidePoint(64.0D, 0.0D);
            Vec3 outside = new Vec3(
                    segment.midpointX() + segment.outward().getStepX() * 0.5D,
                    64.0D,
                    segment.midpointZ() + segment.outward().getStepZ() * 0.5D);
            assertTrue(geometry.isCanonicalBlock(BlockPos.containing(inside)));
            assertFalse(geometry.isCanonicalBlock(BlockPos.containing(outside)));
        }
    }

    @Test
    void offsetSquareQueryBoxesCoverBothFramesAtEastWestTJunctions() {
        OffsetSquareTileGeometry geometry = offsetSquare(16);
        double edge = geometry.tileSizeBlocks() * 0.5D;
        for (double x : List.of(-edge, edge)) {
            AABB visible = new AABB(x - 2.0D, 60.0D, -2.0D, x + 2.0D, 68.0D, 2.0D);
            List<AABB> boxes = geometry.canonicalQueryBoxes(visible);

            assertTrue(boxes.size() >= 3, "expected canonical plus both offset neighbor frames at x=" + x);
            assertTrue(boxes.stream().anyMatch(box -> box.minZ < 0.0D));
            assertTrue(boxes.stream().anyMatch(box -> box.maxZ > 0.0D));
            assertTrue(boxes.stream().allMatch(box ->
                    box.minX >= -edge && box.maxX <= edge && box.minZ >= -edge && box.maxZ <= edge));
        }
    }

    @Test
    void offsetSquareQueryBoxesCoverAllSixNeighborFrames() {
        OffsetSquareTileGeometry geometry = offsetSquare(16);
        Vec3 canonicalCenter = new Vec3(8.0D, 64.0D, 8.0D);

        for (TileGeometry.LatticeCoordinate coordinate : geometry.neighboringTiles()) {
            ChunkPos translation = geometry.latticeTranslation(coordinate);
            Vec3 visibleCenter = canonicalCenter.add(
                    translation.x() * 16.0D,
                    0.0D,
                    translation.z() * 16.0D);
            AABB visibleBox = AABB.ofSize(visibleCenter, 3.0D, 3.0D, 3.0D);
            assertTrue(
                    geometry.canonicalQueryBoxes(visibleBox).stream()
                            .anyMatch(box -> box.inflate(1.0E-7D).contains(canonicalCenter)),
                    "query boxes missed " + coordinate.seamLabel());
        }
    }

    @Test
    void offsetSquareQuerySpanningOnePeriodPreservesTheNarrowAxis() {
        OffsetSquareTileGeometry geometry = offsetSquare(2);
        AABB visible = new AABB(-16.0D, 0.0D, -1.0D, 16.0D, 1.0D, 1.0D);
        List<AABB> boxes = geometry.canonicalQueryBoxes(visible);

        assertTrue(containsPoint(boxes, 0.0D, 0.5D, 0.0D));
        assertFalse(containsPoint(boxes, 0.0D, 0.5D, 10.0D));
    }

    @Test
    void offsetSquareLongitudeAndAtlasProjectionAreLatticeInvariant() {
        OffsetSquareTileGeometry geometry = offsetSquare(16);
        AtlasTorusProjection projection = AtlasTorusProjection.create(geometry.tiling());
        Vec3 point = new Vec3(37.25D, 0.0D, -51.75D);
        AtlasTorusProjection.UnitPoint pixelPoint = projection.project(point);
        double solarOffset = CoordUtil.longitudeOffsetTicks(geometry.tiling(), point.x());

        assertEquals(geometry.tileSizeBlocks(), geometry.longitudePeriodBlocks());
        assertNotEquals(
                projection.identity(),
                AtlasTorusProjection.create(new DimensionTiling(
                        TilingMode.SQUARE,
                        true,
                        16,
                        TerrainMode.EDGE_BLEND)).identity());
        assertNotEquals(projection.identity(), AtlasTorusProjection.create(hex(16).tiling()).identity());
        for (TileGeometry.LatticeCoordinate coordinate : geometry.neighboringTiles()) {
            ChunkPos translation = geometry.latticeTranslation(coordinate);
            assertEquals(
                    solarOffset,
                    CoordUtil.longitudeOffsetTicks(
                            geometry.tiling(),
                            point.x() + translation.x() * 16.0D),
                    1.0E-9D);
            assertUnitPointEquals(
                    pixelPoint,
                    projection.project(
                            point.x() + translation.x() * 16.0D,
                            point.z() + translation.z() * 16.0D));
        }
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
        List<Integer> columnHeights = new java.util.ArrayList<>();
        int canonicalChunks = 0;
        for (int x = -4; x <= 3; x++) {
            int columnHeight = 0;
            for (int z = -4; z <= 3; z++) {
                if (geometry.isCanonicalChunk(new ChunkPos(x, z))) {
                    columnHeight++;
                    canonicalChunks++;
                }
            }
            columnHeights.add(columnHeight);
        }

        assertEquals(8, geometry.widthChunks());
        assertEquals(8, geometry.heightChunks());
        assertEquals(6, geometry.horizontalSpacingChunks());
        assertEquals("hex-east-west-v2", geometry.geometryRevision());
        assertEquals(48, canonicalChunks);
        assertEquals(List.of(2, 6, 8, 8, 8, 8, 6, 2), columnHeights);
        assertEquals(new ChunkPos(6, 4), geometry.latticeA());
        assertEquals(new ChunkPos(0, 8), geometry.latticeB());
        assertEquals(new ChunkPos(6, -4), geometry.latticeC());
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
    void everySupportedHexWidthHasOneTranslationInvariantOwnerPerOrbit() {
        List<TileGeometry.LatticeCoordinate> translations = List.of(
                new TileGeometry.LatticeCoordinate(1, 0),
                new TileGeometry.LatticeCoordinate(-1, 0),
                new TileGeometry.LatticeCoordinate(0, 1),
                new TileGeometry.LatticeCoordinate(0, -1),
                new TileGeometry.LatticeCoordinate(1, -1),
                new TileGeometry.LatticeCoordinate(-1, 1),
                new TileGeometry.LatticeCoordinate(3, -4),
                new TileGeometry.LatticeCoordinate(-7, 5));
        Random random = new Random(0x48455856324L);
        List<Integer> widths = new ArrayList<>();
        for (int width = 8; width <= 256; width += 4) {
            widths.add(width);
        }
        widths.addAll(List.of(320, 512));

        for (int width : widths) {
            HexTileGeometry geometry = hex(width);
            int maskChunks = 0;
            for (int x = -width; x <= width; x++) {
                for (int z = -geometry.heightChunks(); z <= geometry.heightChunks(); z++) {
                    ChunkPos owner = new ChunkPos(x, z);
                    if (!geometry.isCanonicalChunk(owner)) {
                        continue;
                    }
                    maskChunks++;
                    assertEquals(owner, geometry.canonicalChunk(x, z));
                    for (TileGeometry.LatticeCoordinate coordinate : translations) {
                        ChunkPos translation = geometry.latticeTranslation(coordinate);
                        ChunkPos raw = new ChunkPos(owner.x() + translation.x(), owner.z() + translation.z());
                        assertEquals(owner, geometry.canonicalChunk(raw.x(), raw.z()));
                        assertEquals(coordinate, geometry.latticeCoordinate(raw));
                        assertEquals(translation, geometry.latticeTranslation(geometry.latticeCoordinate(raw)));

                        BlockPos ownerBlock = new BlockPos(owner.getMinBlockX() + 7, 80, owner.getMinBlockZ() + 11);
                        BlockPos rawBlock = ownerBlock.offset(translation.x() * 16, 0, translation.z() * 16);
                        assertEquals(
                                ownerBlock,
                                geometry.canonicalBlock(rawBlock.getX(), rawBlock.getY(), rawBlock.getZ()));
                    }
                }
            }
            assertEquals(geometry.canonicalChunkCount(), maskChunks, "mask area at width " + width);

            for (int sample = 0; sample < 64; sample++) {
                ChunkPos raw = new ChunkPos(
                        random.nextInt(width * 10 + 1) - width * 5,
                        random.nextInt(geometry.heightChunks() * 10 + 1) - geometry.heightChunks() * 5);
                ChunkPos owner = geometry.canonicalChunk(raw.x(), raw.z());
                TileGeometry.LatticeCoordinate shift = translations.get(random.nextInt(translations.size()));
                ChunkPos translation = geometry.latticeTranslation(shift);
                assertEquals(
                        owner,
                        geometry.canonicalChunk(raw.x() + translation.x(), raw.z() + translation.z()));
            }
        }
    }

    @Test
    void widthTwelveHexBoundaryTieHasOneOwner() {
        HexTileGeometry geometry = hex(12);

        assertEquals(new ChunkPos(4, 2), geometry.canonicalChunk(4, 2));
        assertEquals(new ChunkPos(4, 2), geometry.canonicalChunk(-5, -3));
        assertEquals(new TileGeometry.LatticeCoordinate(-1, 0), geometry.latticeCoordinate(new ChunkPos(-5, -3)));
        assertEquals(90, geometry.canonicalChunkCount());
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
    void hexQuerySpanningOneBoundingWidthPreservesTheNarrowAxis() {
        HexTileGeometry geometry = hex(8);
        AABB visible = new AABB(-64.0D, 0.0D, -1.0D, 64.0D, 1.0D, 1.0D);
        List<AABB> boxes = geometry.canonicalQueryBoxes(visible);

        assertTrue(containsPoint(boxes, 0.0D, 0.5D, 0.0D));
        assertTrue(geometry.isCanonicalChunk(new ChunkPos(0, 2)));
        assertFalse(containsPoint(boxes, 8.0D, 0.5D, 40.0D));
    }

    @Test
    void coupledQuerySlicesMatchBruteForceAliasPoints() {
        for (TileGeometry geometry : List.of(offsetSquare(8), hex(8))) {
            double period = geometry.tileSizeBlocks();
            List<AABB> visibleBoxes = List.of(
                    new AABB(-period * 0.5D - 2.0D, 0.0D, -2.0D,
                            -period * 0.5D + 2.0D, 1.0D, 2.0D),
                    new AABB(-period * 0.5D, 0.0D, -1.0D,
                            period * 0.5D, 1.0D, 1.0D),
                    new AABB(-1.0D, 0.0D, -period * 0.5D,
                            1.0D, 1.0D, period * 0.5D),
                    new AABB(-period * 0.5D - 0.5D, 0.0D, -3.0D,
                            period * 0.5D + 0.5D, 1.0D, 3.0D));
            List<Vec3> canonicalSamples = canonicalChunkCenters(geometry, 16);
            for (AABB visible : visibleBoxes) {
                List<AABB> slices = geometry.canonicalQueryBoxes(visible);
                for (Vec3 sample : canonicalSamples) {
                    assertEquals(
                            hasVisibleAlias(geometry, visible, sample, 8),
                            containsPoint(slices, sample.x(), sample.y(), sample.z()),
                            "query mismatch for " + geometry.geometryRevision() + ", " + visible + ", " + sample);
                }
            }
        }
    }

    @Test
    void dualBasisRadiiIncludeNearCancellingLatticeAliases() {
        HexTileGeometry hex = hex(8);
        LatticeMath.CoefficientRadii hexRadii = LatticeMath.coefficientRadii(hex.latticeBasis(), 960.0D);
        LatticeMath.CoefficientBounds hexBounds =
                LatticeMath.coefficientBounds(hex.latticeBasis(), 0.0D, 0.0D, 960.0D);

        assertEquals(10, hexRadii.k());
        assertEquals(10, hexRadii.l());
        assertTrue(hexBounds.contains(10, -5));

        OffsetSquareTileGeometry offset = offsetSquare(2);
        LatticeMath.CoefficientRadii offsetRadii =
                LatticeMath.coefficientRadii(offset.latticeBasis(), 320.0D);
        LatticeMath.CoefficientBounds offsetBounds =
                LatticeMath.coefficientBounds(offset.latticeBasis(), 0.0D, 0.0D, 320.0D);
        assertTrue(offsetRadii.k() >= 10);
        assertTrue(offsetBounds.contains(10, -5));
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
    void canonicalChunkCountUsesTheGeometryLatticeArea() {
        TileGeometry square = TileGeometry.create(
                new DimensionTiling(TilingMode.SQUARE, true, 16, TerrainMode.COMPACT_TORUS));
        HexTileGeometry geometry = hex(16);

        assertEquals(16 * 16, square.canonicalChunkCount());
        assertEquals(12 * 14, geometry.canonicalChunkCount());
        int maskChunks = 0;
        for (int x = -32; x <= 32; x++) {
            for (int z = -32; z <= 32; z++) {
                if (geometry.isCanonicalChunk(new ChunkPos(x, z))) {
                    maskChunks++;
                }
            }
        }
        assertEquals(geometry.canonicalChunkCount(), maskChunks);
    }

    @Test
    void hexLongitudeIsConstantNorthToSouthAndAcrossEveryAlias() {
        HexTileGeometry geometry = hex(16);
        DimensionTiling tiling = geometry.tiling();
        double x = 37.25D;
        double expected = CoordUtil.longitudeOffsetTicks(tiling, x);

        assertEquals(geometry.horizontalSpacingChunks() * 16, geometry.longitudePeriodBlocks());
        assertEquals(expected, CoordUtil.longitudeOffsetTicks(tiling, x), 1.0E-12D);
        assertEquals(
                CoordUtil.longitudeOffsetTicks(tiling, new BlockPos(37, 64, -10_000)),
                CoordUtil.longitudeOffsetTicks(tiling, new BlockPos(37, 64, 10_000)),
                1.0E-12D);
        for (ChunkPos translation : List.of(
                geometry.latticeA(),
                geometry.latticeB(),
                geometry.latticeC(),
                new ChunkPos(-geometry.latticeA().x(), -geometry.latticeA().z()),
                new ChunkPos(-geometry.latticeB().x(), -geometry.latticeB().z()),
                new ChunkPos(-geometry.latticeC().x(), -geometry.latticeC().z()))) {
            assertEquals(
                    expected,
                    CoordUtil.longitudeOffsetTicks(tiling, x + translation.x() * 16.0D),
                    1.0E-9D);
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

    @Test
    void squareAtlasProjectionPreservesExistingWorldAxes() {
        DimensionTiling tiling = new DimensionTiling(TilingMode.SQUARE, true, 16, TerrainMode.COMPACT_TORUS);
        AtlasTorusProjection projection = AtlasTorusProjection.create(tiling);

        assertEquals(new AtlasTorusProjection.UnitPoint(0.0D, 0.0D), projection.project(-128.0D, -128.0D));
        assertEquals(new AtlasTorusProjection.UnitPoint(0.5D, 0.5D), projection.project(0.0D, 0.0D));
        assertEquals(new AtlasTorusProjection.Pixel(256, 256), projection.pixel(0.0D, 0.0D, 512));
    }

    @Test
    void hexAtlasProjectionIsInvariantUnderBothLatticeTranslations() {
        HexTileGeometry geometry = hex(16);
        AtlasTorusProjection projection = AtlasTorusProjection.create(geometry.tiling());
        Vec3 point = new Vec3(37.25D, 0.0D, -51.75D);
        AtlasTorusProjection.UnitPoint expected = projection.project(point);

        for (ChunkPos translation : List.of(geometry.latticeA(), geometry.latticeB(), geometry.latticeC())) {
            assertUnitPointEquals(
                    expected,
                    projection.project(
                            point.x() + translation.x() * 16.0D,
                            point.z() + translation.z() * 16.0D));
        }
    }

    @Test
    void atlasPixelCentersRoundTripThroughCanonicalHexSamples() {
        AtlasTorusProjection projection = AtlasTorusProjection.create(hex(16).tiling());

        for (int v : List.of(0, 1, 127, 255, 511)) {
            for (int u : List.of(0, 1, 127, 255, 511)) {
                Vec3 sample = projection.canonicalPixelCenter(u, v, 512);
                assertEquals(new AtlasTorusProjection.Pixel(u, v), projection.pixel(sample.x(), sample.z(), 512));
            }
        }
        assertEquals(12 * 14, projection.canonicalChunkCount());
    }

    @Test
    void hexSurveyMarkersUseTheViewerNearestLatticeCopy() {
        DimensionTiling tiling = hex(16).tiling();
        int cell = GlobeAtlasSurveyWindows.cellIndex(tiling, 7, 0, -8, 0);

        assertEquals(253 + 263 * GlobeAtlasSurveyWindows.PLACED_WINDOW_CHUNKS, cell);
    }

    private static HexTileGeometry hex(int tileSizeChunks) {
        return new HexTileGeometry(new DimensionTiling(TilingMode.HEX, true, tileSizeChunks, TerrainMode.EDGE_BLEND));
    }

    private static OffsetSquareTileGeometry offsetSquare(int tileSizeChunks) {
        return new OffsetSquareTileGeometry(
                new DimensionTiling(TilingMode.OFFSET_SQUARE, true, tileSizeChunks, TerrainMode.EDGE_BLEND));
    }

    private static ChunkPos delta(ChunkPos from, ChunkPos to) {
        return new ChunkPos(to.x() - from.x(), to.z() - from.z());
    }

    private static List<Vec3> canonicalChunkCenters(TileGeometry geometry, int range) {
        List<Vec3> centers = new ArrayList<>();
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (geometry.isCanonicalChunk(chunk)) {
                    centers.add(new Vec3(chunk.getMiddleBlockX(), 0.5D, chunk.getMiddleBlockZ()));
                }
            }
        }
        return centers;
    }

    private static boolean hasVisibleAlias(
            TileGeometry geometry,
            AABB visible,
            Vec3 canonical,
            int coordinateRadius) {
        for (int k = -coordinateRadius; k <= coordinateRadius; k++) {
            for (int l = -coordinateRadius; l <= coordinateRadius; l++) {
                Vec3 alias = geometry.translatedAlias(canonical, new TileGeometry.LatticeCoordinate(k, l));
                if (containsPoint(List.of(visible), alias.x(), alias.y(), alias.z())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsPoint(List<AABB> boxes, double x, double y, double z) {
        return boxes.stream().anyMatch(box ->
                x >= box.minX && x < box.maxX
                        && y >= box.minY && y < box.maxY
                        && z >= box.minZ && z < box.maxZ);
    }

    private static void assertUnitPointEquals(
            final AtlasTorusProjection.UnitPoint expected,
            final AtlasTorusProjection.UnitPoint actual) {
        assertEquals(expected.u(), actual.u(), 1.0E-12D);
        assertEquals(expected.v(), actual.v(), 1.0E-12D);
    }
}
