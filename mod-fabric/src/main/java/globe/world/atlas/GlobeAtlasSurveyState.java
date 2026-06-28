package globe.world.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import globe.world.GlobeWorld;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public class GlobeAtlasSurveyState extends SavedData {
    private static final int CURRENT_VERSION = 1;
    private static final Codec<GlobeAtlasSurveyState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("version", CURRENT_VERSION).forGetter(data -> data.version),
            Codec.INT.fieldOf("tileSizeBlocks").forGetter(data -> data.tileSizeBlocks),
            Identifier.CODEC.listOf().optionalFieldOf("visitedBiomes", List.of()).forGetter(data -> List.copyOf(data.visitedBiomes)),
            Codec.LONG.listOf().optionalFieldOf("visitedChunks", List.of()).forGetter(data -> List.copyOf(data.visitedChunks)),
            Codec.BOOL.optionalFieldOf("completedBiomes", false).forGetter(data -> data.completedBiomes)
    ).apply(instance, GlobeAtlasSurveyState::new));
    private static final SavedDataType<GlobeAtlasSurveyState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "atlas_survey"),
            GlobeAtlasSurveyState::empty,
            CODEC,
            DataFixTypes.SAVED_DATA_MAP_DATA);

    private final int version;
    private final int tileSizeBlocks;
    private final Set<Identifier> visitedBiomes = new TreeSet<>();
    private final Set<Long> visitedChunks = new TreeSet<>();
    private boolean completedBiomes;
    private int revision = 1;

    public GlobeAtlasSurveyState() {
        this(CURRENT_VERSION, GlobeAtlasSurvey.LARGE_TILE_CUTOFF_CHUNKS * 16 + 16, List.of(), List.of(), false);
    }

    private GlobeAtlasSurveyState(
            final int version,
            final int tileSizeBlocks,
            final List<Identifier> visitedBiomes,
            final List<Long> visitedChunks,
            final boolean completedBiomes) {
        this.version = version;
        this.tileSizeBlocks = tileSizeBlocks;
        this.visitedBiomes.addAll(visitedBiomes);
        this.visitedChunks.addAll(visitedChunks);
        this.completedBiomes = completedBiomes;
    }

    public static GlobeAtlasSurveyState get(final ServerLevel level, final DimensionTiling tiling) {
        GlobeAtlasSurveyState existing = level.getDataStorage().get(TYPE);
        if (existing != null && existing.matches(tiling)) {
            return existing;
        }

        GlobeAtlasSurveyState created = new GlobeAtlasSurveyState(
                CURRENT_VERSION,
                tiling.tileSizeBlocks(),
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
        int chunkX = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(Mth.floor(x)));
        int chunkZ = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(Mth.floor(z)));
        if (this.visitedChunks.add(new ChunkPos(chunkX, chunkZ).pack())) {
            this.changed();
            return true;
        }
        return false;
    }

    public int revision() {
        return this.revision;
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

    private boolean matches(final DimensionTiling tiling) {
        return this.version == CURRENT_VERSION && this.tileSizeBlocks == tiling.tileSizeBlocks();
    }

    private void changed() {
        this.revision++;
        this.setDirty();
    }

    private static GlobeAtlasSurveyState empty() {
        return new GlobeAtlasSurveyState();
    }
}
