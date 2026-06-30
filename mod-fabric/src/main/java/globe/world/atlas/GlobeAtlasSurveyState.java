package globe.world.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import globe.world.GlobeWorld;
import globe.world.config.TilingMode;
import globe.world.topology.AtlasTorusProjection;
import globe.world.topology.TileGeometry;
import globe.world.util.DimensionTiling;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.LongConsumer;

public class GlobeAtlasSurveyState extends SavedData {
    private static final int CURRENT_VERSION = 2;
    private static final Codec<ChunkBiomeEntry> CHUNK_BIOME_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("chunk").forGetter(ChunkBiomeEntry::chunk),
            Identifier.CODEC.fieldOf("biome").forGetter(ChunkBiomeEntry::biome)
    ).apply(instance, ChunkBiomeEntry::new));
    private static final Codec<GlobeAtlasSurveyState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("version", 1).forGetter(data -> data.version),
            Codec.INT.fieldOf("tileSizeBlocks").forGetter(data -> data.tileSizeBlocks),
            TilingMode.CODEC.optionalFieldOf("tilingMode", TilingMode.SQUARE).forGetter(data -> data.tilingMode),
            Codec.STRING.optionalFieldOf("projection", "legacy-square-xz-v1").forGetter(data -> data.projectionIdentity),
            Identifier.CODEC.listOf().optionalFieldOf("visitedBiomes", List.of()).forGetter(data -> List.copyOf(data.visitedBiomes)),
            Codec.LONG.listOf().optionalFieldOf("visitedChunks", List.of()).forGetter(data -> List.copyOf(data.visitedChunks)),
            CHUNK_BIOME_CODEC.listOf().optionalFieldOf("chunkBiomes", List.of()).forGetter(data -> data.chunkBiomeEntries()),
            Codec.BOOL.optionalFieldOf("completedBiomes", false).forGetter(data -> data.completedBiomes)
    ).apply(instance, GlobeAtlasSurveyState::new));
    private static final SavedDataType<GlobeAtlasSurveyState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "atlas_survey"),
            GlobeAtlasSurveyState::empty,
            CODEC,
            DataFixTypes.SAVED_DATA_MAP_DATA);

    private final int version;
    private final int tileSizeBlocks;
    private final TilingMode tilingMode;
    private final String projectionIdentity;
    private final Set<Identifier> visitedBiomes = new TreeSet<>();
    private final Set<Long> visitedChunks = new TreeSet<>();
    private final Map<Long, Identifier> chunkBiomes = new TreeMap<>();
    private boolean completedBiomes;
    private int revision = 1;

    public GlobeAtlasSurveyState() {
        this(
                CURRENT_VERSION,
                GlobeAtlasSurvey.LARGE_TILE_CUTOFF_CHUNKS * 16 + 16,
                TilingMode.SQUARE,
                "legacy-square-xz-v1",
                List.of(),
                List.of(),
                List.of(),
                false);
    }

    private GlobeAtlasSurveyState(
            final int version,
            final int tileSizeBlocks,
            final TilingMode tilingMode,
            final String projectionIdentity,
            final List<Identifier> visitedBiomes,
            final List<Long> visitedChunks,
            final List<ChunkBiomeEntry> chunkBiomes,
            final boolean completedBiomes) {
        this.version = version;
        this.tileSizeBlocks = tileSizeBlocks;
        this.tilingMode = tilingMode;
        this.projectionIdentity = projectionIdentity;
        this.visitedBiomes.addAll(visitedBiomes);
        this.visitedChunks.addAll(visitedChunks);
        for (ChunkBiomeEntry entry : chunkBiomes) {
            this.chunkBiomes.put(entry.chunk(), entry.biome());
            this.visitedChunks.add(entry.chunk());
        }
        this.completedBiomes = completedBiomes;
    }

    public static GlobeAtlasSurveyState get(final ServerLevel level, final DimensionTiling tiling) {
        GlobeAtlasSurveyState existing = level.getDataStorage().get(TYPE);
        if (existing != null && existing.matches(tiling)) {
            return existing;
        }

        AtlasTorusProjection projection = AtlasTorusProjection.create(tiling);
        if (existing != null) {
            GlobeWorld.LOGGER.warn(
                    "Resetting Atlas survey data because projection {} does not match {}",
                    existing.projectionIdentity,
                    projection.identity());
        }
        GlobeAtlasSurveyState created = new GlobeAtlasSurveyState(
                CURRENT_VERSION,
                tiling.tileSizeBlocks(),
                tiling.mode(),
                projection.identity(),
                List.of(),
                List.of(),
                List.of(),
                false);
        level.getDataStorage().set(TYPE, created);
        return created;
    }

    public static Optional<GlobeAtlasSurveyState> getIfPresent(final ServerLevel level, final DimensionTiling tiling) {
        GlobeAtlasSurveyState existing = level.getDataStorage().get(TYPE);
        return existing != null && existing.matches(tiling) ? Optional.of(existing) : Optional.empty();
    }

    public boolean recordBiome(final Identifier biomeId) {
        if (this.visitedBiomes.add(biomeId)) {
            this.changed();
            return true;
        }
        return false;
    }

    public boolean recordCompletedBiomes() {
        if (!this.completedBiomes) {
            this.completedBiomes = true;
            this.changed();
            return true;
        }
        return false;
    }

    public boolean recordVisitedChunk(final DimensionTiling tiling, final double x, final double z) {
        return this.recordVisitedChunk(tiling, x, z, null);
    }

    public boolean recordVisitedChunk(final DimensionTiling tiling, final double x, final double z, final Identifier biomeId) {
        TileGeometry geometry = TileGeometry.create(tiling);
        ChunkPos canonical = geometry.canonicalChunk(
                SectionPos.blockToSectionCoord(Mth.floor(x)),
                SectionPos.blockToSectionCoord(Mth.floor(z)));
        return this.recordVisitedChunk(tiling, canonical.x(), canonical.z(), biomeId);
    }

    public boolean recordVisitedChunk(final DimensionTiling tiling, final int chunkX, final int chunkZ) {
        return this.recordVisitedChunk(tiling, chunkX, chunkZ, null);
    }

    public boolean recordVisitedChunk(final DimensionTiling tiling, final int chunkX, final int chunkZ, final Identifier biomeId) {
        ChunkPos canonical = TileGeometry.create(tiling).canonicalChunk(chunkX, chunkZ);
        long chunk = canonical.pack();
        boolean changed = this.visitedChunks.add(chunk);
        if (biomeId != null) {
            changed |= this.visitedBiomes.add(biomeId);
            if (!this.chunkBiomes.containsKey(chunk)) {
                this.chunkBiomes.put(chunk, biomeId);
                changed = true;
            }
        }
        if (changed) {
            this.changed();
        }
        return changed;
    }

    public int revision() {
        return 31 * this.revision + this.projectionIdentity.hashCode();
    }

    public int biomeCount() {
        return this.visitedBiomes.size();
    }

    public boolean completedBiomes() {
        return this.completedBiomes;
    }

    public int visitedChunks() {
        return this.visitedChunks.size();
    }

    public boolean isVisitedChunk(final int chunkX, final int chunkZ) {
        return this.visitedChunks.contains(new ChunkPos(chunkX, chunkZ).pack());
    }

    public Optional<Identifier> visitedChunkBiome(final int chunkX, final int chunkZ) {
        return Optional.ofNullable(this.chunkBiomes.get(new ChunkPos(chunkX, chunkZ).pack()));
    }

    public Optional<Identifier> visitedChunkBiome(final long chunk) {
        return Optional.ofNullable(this.chunkBiomes.get(chunk));
    }

    public void forEachVisitedChunk(final LongConsumer consumer) {
        this.visitedChunks.forEach(consumer::accept);
    }

    public List<Long> visitedChunkKeys() {
        return List.copyOf(this.visitedChunks);
    }

    public List<ChunkBiomeEntry> chunkBiomeEntries() {
        List<ChunkBiomeEntry> entries = new ArrayList<>(this.chunkBiomes.size());
        for (Map.Entry<Long, Identifier> entry : this.chunkBiomes.entrySet()) {
            entries.add(new ChunkBiomeEntry(entry.getKey(), entry.getValue()));
        }
        return entries;
    }

    private boolean matches(final DimensionTiling tiling) {
        if (this.tileSizeBlocks != tiling.tileSizeBlocks()) {
            return false;
        }
        if (this.version == 1) {
            return tiling.mode() == TilingMode.SQUARE;
        }
        return this.version == CURRENT_VERSION
                && this.tilingMode == tiling.mode()
                && this.projectionIdentity.equals(AtlasTorusProjection.create(tiling).identity());
    }

    private void changed() {
        this.revision++;
        this.setDirty();
    }

    private static GlobeAtlasSurveyState empty() {
        return new GlobeAtlasSurveyState();
    }

    public record ChunkBiomeEntry(long chunk, Identifier biome) {
    }
}
