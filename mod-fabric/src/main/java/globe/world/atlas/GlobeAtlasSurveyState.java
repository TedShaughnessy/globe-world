package globe.world.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import globe.world.GlobeWorld;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public class GlobeAtlasSurveyState extends SavedData {
    private static final int CURRENT_VERSION = 1;
    private static final int VISITED_CELL_BYTES = (GlobeAtlasSurvey.COVERAGE_CELL_COUNT + 7) / 8;
    private static final Codec<GlobeAtlasSurveyState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("version", CURRENT_VERSION).forGetter(data -> data.version),
            Codec.INT.fieldOf("tileSizeBlocks").forGetter(data -> data.tileSizeBlocks),
            Identifier.CODEC.listOf().optionalFieldOf("visitedBiomes", List.of()).forGetter(data -> List.copyOf(data.visitedBiomes)),
            Codec.BYTE_BUFFER.optionalFieldOf("visitedCells", ByteBuffer.allocate(0)).forGetter(data -> ByteBuffer.wrap(data.visitedCells))
    ).apply(instance, GlobeAtlasSurveyState::new));
    private static final SavedDataType<GlobeAtlasSurveyState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "atlas_survey"),
            GlobeAtlasSurveyState::empty,
            CODEC,
            DataFixTypes.SAVED_DATA_MAP_DATA);

    private final int version;
    private final int tileSizeBlocks;
    private final Set<Identifier> visitedBiomes = new TreeSet<>();
    private byte[] visitedCells;
    private int revision = 1;

    public GlobeAtlasSurveyState() {
        this(CURRENT_VERSION, GlobeAtlasSurvey.LARGE_TILE_CUTOFF_CHUNKS * 16 + 16, List.of(), ByteBuffer.allocate(0));
    }

    private GlobeAtlasSurveyState(
            final int version,
            final int tileSizeBlocks,
            final List<Identifier> visitedBiomes,
            final ByteBuffer visitedCells) {
        this.version = version;
        this.tileSizeBlocks = tileSizeBlocks;
        this.visitedBiomes.addAll(visitedBiomes);
        this.visitedCells = normalize(copyBytes(visitedCells), VISITED_CELL_BYTES);
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
                ByteBuffer.allocate(0));
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

    public boolean recordVisitedCell(final DimensionTiling tiling, final double x, final double z) {
        int index = cellIndexForCanonicalBlock(tiling, x, z);
        if (this.isVisited(index)) {
            return false;
        }

        setVisited(this.visitedCells, index);
        this.changed();
        return true;
    }

    public int revision() {
        return this.revision;
    }

    public int biomeCount() {
        return this.visitedBiomes.size();
    }

    public int visitedCells() {
        int cells = 0;
        for (int i = 0; i < GlobeAtlasSurvey.COVERAGE_CELL_COUNT; i++) {
            if (this.isVisited(i)) {
                cells++;
            }
        }
        return cells;
    }

    public int totalCells() {
        return GlobeAtlasSurvey.COVERAGE_CELL_COUNT;
    }

    public double visitedCellPercent() {
        return this.visitedCells() * 100.0D / GlobeAtlasSurvey.COVERAGE_CELL_COUNT;
    }

    public byte[] copyVisitedCells() {
        return java.util.Arrays.copyOf(this.visitedCells, this.visitedCells.length);
    }

    public boolean isVisited(final int index) {
        return (this.visitedCells[index >> 3] & (1 << (index & 7))) != 0;
    }

    public static int cellIndexForCanonicalBlock(final DimensionTiling tiling, final double x, final double z) {
        int cellX = cellCoord(tiling, x);
        int cellZ = cellCoord(tiling, z);
        return cellX + cellZ * GlobeAtlasSurvey.COVERAGE_GRID_SIZE;
    }

    private static int cellCoord(final DimensionTiling tiling, final double block) {
        double normalized = (CoordUtil.wrapBlock(tiling, block) + tiling.tileSizeBlocks() / 2.0D) / tiling.tileSizeBlocks();
        return Math.floorMod(Mth.floor(normalized * GlobeAtlasSurvey.COVERAGE_GRID_SIZE), GlobeAtlasSurvey.COVERAGE_GRID_SIZE);
    }

    private static void setVisited(final byte[] visitedCells, final int index) {
        visitedCells[index >> 3] = (byte)(visitedCells[index >> 3] | (1 << (index & 7)));
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

    private static byte[] copyBytes(final ByteBuffer buffer) {
        ByteBuffer copy = buffer.slice();
        byte[] result = new byte[copy.remaining()];
        copy.get(result);
        return result;
    }

    private static byte[] normalize(final byte[] source, final int size) {
        byte[] result = new byte[size];
        System.arraycopy(source, 0, result, 0, Math.min(source.length, size));
        return result;
    }
}
