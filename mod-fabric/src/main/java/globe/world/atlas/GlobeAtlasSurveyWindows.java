package globe.world.atlas;

import globe.world.network.GlobeAtlasSurveyWindowPayload;
import globe.world.topology.TileGeometry;
import globe.world.util.DimensionTiling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GlobeAtlasSurveyWindows {
    public static final int HELD_WINDOW_CHUNKS = GlobeAtlasSurveyWindowPayload.HELD_WINDOW_CHUNKS;
    public static final int PLACED_WINDOW_CHUNKS = GlobeAtlasSurveyWindowPayload.PLACED_WINDOW_CHUNKS;
    private static final int MAX_PALETTE_INDEX = 255;
    private static final int SOURCE_ATLAS_MARKER = 0xFF8FE8FF;
    private static final int ATLAS_MARKER = 0xFFFFD46A;

    private GlobeAtlasSurveyWindows() {
    }

    public static GlobeAtlasSurveyWindowPayload held(
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeAtlasSurveyState survey,
            final ServerPlayer player,
            final List<GlobeAtlasPowerState.Entry> atlases) {
        TileGeometry geometry = TileGeometry.create(tiling);
        Vec3 canonical = geometry.canonicalBlock(player.position());
        ChunkPos center = geometry.canonicalChunk(
                SectionPos.blockToSectionCoord(Mth.floor(canonical.x())),
                SectionPos.blockToSectionCoord(Mth.floor(canonical.z())));
        List<GlobeAtlasSurveyWindowPayload.Marker> markers = new ArrayList<>();
        addAtlasMarkers(tiling, center.x(), center.z(), HELD_WINDOW_CHUNKS, atlases, null, markers);
        return create(level, tiling, survey, center.x(), center.z(), HELD_WINDOW_CHUNKS, markers);
    }

    public static GlobeAtlasSurveyWindowPayload placed(
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeAtlasSurveyState survey,
            final BlockPos atlasPos,
            final List<GlobeAtlasPowerState.Entry> atlases) {
        ChunkPos center = TileGeometry.create(tiling).canonicalChunk(
                SectionPos.blockToSectionCoord(atlasPos.getX()),
                SectionPos.blockToSectionCoord(atlasPos.getZ()));
        List<GlobeAtlasSurveyWindowPayload.Marker> markers = new ArrayList<>();
        markers.add(new GlobeAtlasSurveyWindowPayload.Marker(PLACED_WINDOW_CHUNKS / 2, PLACED_WINDOW_CHUNKS / 2, SOURCE_ATLAS_MARKER));
        addAtlasMarkers(tiling, center.x(), center.z(), PLACED_WINDOW_CHUNKS, atlases, atlasPos, markers);
        return create(level, tiling, survey, center.x(), center.z(), PLACED_WINDOW_CHUNKS, markers);
    }

    private static GlobeAtlasSurveyWindowPayload create(
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeAtlasSurveyState survey,
            final int centerChunkX,
            final int centerChunkZ,
            final int windowChunks,
            final List<GlobeAtlasSurveyWindowPayload.Marker> markers) {
        int cells = windowChunks * windowChunks;
        int discoveredBytes = (cells + 7) / 8;
        byte[] discovered = new byte[discoveredBytes];
        byte[] biomeIndexes = new byte[cells];
        List<Identifier> palette = new ArrayList<>();
        Map<Identifier, Integer> paletteIndexes = new HashMap<>();

        if (survey.visitedChunks() > cells) {
            fillByWindowCells(tiling, survey, centerChunkX, centerChunkZ, windowChunks, discovered, biomeIndexes, palette, paletteIndexes);
        } else {
            survey.forEachVisitedChunk(chunkKey -> {
                ChunkPos chunk = ChunkPos.unpack(chunkKey);
                int cell = cellIndex(tiling, centerChunkX, centerChunkZ, windowChunks, chunk.x(), chunk.z());
                if (cell >= 0) {
                    fillCell(survey, chunkKey, cell, discovered, biomeIndexes, palette, paletteIndexes);
                }
            });
        }

        return new GlobeAtlasSurveyWindowPayload(
                Level.OVERWORLD.identifier(),
                centerChunkX,
                centerChunkZ,
                windowChunks,
                survey.revision(),
                discovered,
                palette,
                biomeIndexes,
                markers);
    }

    private static void fillByWindowCells(
            final DimensionTiling tiling,
            final GlobeAtlasSurveyState survey,
            final int centerChunkX,
            final int centerChunkZ,
            final int windowChunks,
            final byte[] discovered,
            final byte[] biomeIndexes,
            final List<Identifier> palette,
            final Map<Identifier, Integer> paletteIndexes) {
        int halfWindow = windowChunks / 2;
        TileGeometry geometry = TileGeometry.create(tiling);
        for (int z = 0; z < windowChunks; z++) {
            for (int x = 0; x < windowChunks; x++) {
                ChunkPos canonical = geometry.canonicalChunk(
                        centerChunkX + x - halfWindow,
                        centerChunkZ + z - halfWindow);
                if (!survey.isVisitedChunk(canonical.x(), canonical.z())) {
                    continue;
                }

                int cell = x + z * windowChunks;
                fillCell(
                        survey,
                        canonical.pack(),
                        cell,
                        discovered,
                        biomeIndexes,
                        palette,
                        paletteIndexes);
            }
        }
    }

    private static void fillCell(
            final GlobeAtlasSurveyState survey,
            final long chunkKey,
            final int cell,
            final byte[] discovered,
            final byte[] biomeIndexes,
            final List<Identifier> palette,
            final Map<Identifier, Integer> paletteIndexes) {
        discovered[cell >> 3] = (byte)(discovered[cell >> 3] | (1 << (cell & 7)));
        survey.visitedChunkBiome(chunkKey).ifPresent(biome -> {
            int paletteIndex = paletteIndex(biome, palette, paletteIndexes);
            if (paletteIndex > 0) {
                biomeIndexes[cell] = (byte)paletteIndex;
            }
        });
    }

    private static int paletteIndex(
            final Identifier biome,
            final List<Identifier> palette,
            final Map<Identifier, Integer> paletteIndexes) {
        Integer existing = paletteIndexes.get(biome);
        if (existing != null) {
            return existing;
        }
        if (palette.size() >= MAX_PALETTE_INDEX) {
            return 0;
        }
        int index = palette.size() + 1;
        palette.add(biome);
        paletteIndexes.put(biome, index);
        return index;
    }

    private static void addAtlasMarkers(
            final DimensionTiling tiling,
            final int centerChunkX,
            final int centerChunkZ,
            final int windowChunks,
            final List<GlobeAtlasPowerState.Entry> atlases,
            final BlockPos source,
            final List<GlobeAtlasSurveyWindowPayload.Marker> markers) {
        TileGeometry geometry = TileGeometry.create(tiling);
        BlockPos canonicalSource = source == null
                ? null
                : geometry.canonicalBlock(source.getX(), source.getY(), source.getZ());
        for (GlobeAtlasPowerState.Entry entry : atlases) {
            if (canonicalSource != null && entry.pos().equals(canonicalSource)) {
                continue;
            }

            ChunkPos chunk = geometry.canonicalChunk(
                    SectionPos.blockToSectionCoord(entry.pos().getX()),
                    SectionPos.blockToSectionCoord(entry.pos().getZ()));
            int cell = cellIndex(tiling, centerChunkX, centerChunkZ, windowChunks, chunk.x(), chunk.z());
            if (cell >= 0) {
                markers.add(new GlobeAtlasSurveyWindowPayload.Marker(cell % windowChunks, cell / windowChunks, ATLAS_MARKER));
            }
        }
    }

    public static int cellIndex(
            final DimensionTiling tiling,
            final int centerChunkX,
            final int centerChunkZ,
            final int chunkX,
            final int chunkZ) {
        return cellIndex(tiling, centerChunkX, centerChunkZ, PLACED_WINDOW_CHUNKS, chunkX, chunkZ);
    }

    private static int cellIndex(
            final DimensionTiling tiling,
            final int centerChunkX,
            final int centerChunkZ,
            final int windowChunks,
            final int chunkX,
            final int chunkZ) {
        TileGeometry geometry = TileGeometry.create(tiling);
        ChunkPos center = geometry.canonicalChunk(centerChunkX, centerChunkZ);
        ChunkPos target = geometry.canonicalChunk(chunkX, chunkZ);
        ChunkPos visible = geometry.nearestAlias(target, center);
        int dx = visible.x() - center.x();
        int dz = visible.z() - center.z();
        int halfWindow = windowChunks / 2;
        if (dx < -halfWindow || dx >= halfWindow || dz < -halfWindow || dz >= halfWindow) {
            return -1;
        }
        int x = dx + halfWindow;
        int z = dz + halfWindow;
        return x + z * windowChunks;
    }
}
