package globe.world.atlas;

import globe.world.network.GlobeAtlasSurveyWindowPayload;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

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
        double canonicalX = CoordUtil.wrapBlock(tiling, player.getX());
        double canonicalZ = CoordUtil.wrapBlock(tiling, player.getZ());
        int centerChunkX = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(Mth.floor(canonicalX)));
        int centerChunkZ = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(Mth.floor(canonicalZ)));
        List<GlobeAtlasSurveyWindowPayload.Marker> markers = new ArrayList<>();
        addAtlasMarkers(tiling, centerChunkX, centerChunkZ, HELD_WINDOW_CHUNKS, atlases, null, markers);
        return create(level, tiling, survey, centerChunkX, centerChunkZ, HELD_WINDOW_CHUNKS, markers);
    }

    public static GlobeAtlasSurveyWindowPayload placed(
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeAtlasSurveyState survey,
            final BlockPos atlasPos,
            final List<GlobeAtlasPowerState.Entry> atlases) {
        int centerChunkX = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(atlasPos.getX()));
        int centerChunkZ = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(atlasPos.getZ()));
        List<GlobeAtlasSurveyWindowPayload.Marker> markers = new ArrayList<>();
        markers.add(new GlobeAtlasSurveyWindowPayload.Marker(PLACED_WINDOW_CHUNKS / 2, PLACED_WINDOW_CHUNKS / 2, SOURCE_ATLAS_MARKER));
        addAtlasMarkers(tiling, centerChunkX, centerChunkZ, PLACED_WINDOW_CHUNKS, atlases, atlasPos, markers);
        return create(level, tiling, survey, centerChunkX, centerChunkZ, PLACED_WINDOW_CHUNKS, markers);
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
        Map<Long, Identifier> chunkBiomes = new HashMap<>();
        for (GlobeAtlasSurveyState.ChunkBiomeEntry entry : survey.chunkBiomeEntries()) {
            chunkBiomes.put(entry.chunk(), entry.biome());
        }

        for (long chunkKey : survey.visitedChunkKeys()) {
            ChunkPos chunk = ChunkPos.unpack(chunkKey);
            int cell = cellIndex(tiling, centerChunkX, centerChunkZ, windowChunks, chunk.x(), chunk.z());
            if (cell < 0) {
                continue;
            }

            discovered[cell >> 3] = (byte)(discovered[cell >> 3] | (1 << (cell & 7)));
            Identifier biome = chunkBiomes.get(chunkKey);
            if (biome != null) {
                int paletteIndex = paletteIndex(biome, palette, paletteIndexes);
                if (paletteIndex > 0) {
                    biomeIndexes[cell] = (byte)paletteIndex;
                }
            }
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
        for (GlobeAtlasPowerState.Entry entry : atlases) {
            if (source != null && entry.pos().equals(source)) {
                continue;
            }

            int chunkX = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(entry.pos().getX()));
            int chunkZ = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(entry.pos().getZ()));
            int cell = cellIndex(tiling, centerChunkX, centerChunkZ, windowChunks, chunkX, chunkZ);
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
        int dx = wrappedChunkDelta(tiling, chunkX, centerChunkX);
        int dz = wrappedChunkDelta(tiling, chunkZ, centerChunkZ);
        int halfWindow = windowChunks / 2;
        if (dx < -halfWindow || dx >= halfWindow || dz < -halfWindow || dz >= halfWindow) {
            return -1;
        }
        int x = dx + halfWindow;
        int z = dz + halfWindow;
        return x + z * windowChunks;
    }

    private static int wrappedChunkDelta(final DimensionTiling tiling, final int chunk, final int center) {
        if (!tiling.enabled()) {
            return chunk - center;
        }
        int delta = chunk - center;
        int tileSize = tiling.tileSizeChunks();
        return (int)(delta - Math.rint(delta / (double)tileSize) * tileSize);
    }
}
