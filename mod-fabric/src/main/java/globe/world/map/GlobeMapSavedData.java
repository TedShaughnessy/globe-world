package globe.world.map;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import globe.world.GlobeWorld;
import globe.world.config.TilingMode;
import globe.world.topology.AtlasTorusProjection;
import globe.world.util.DimensionTiling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class GlobeMapSavedData extends SavedData {
    public static final int CURRENT_VERSION = 2;
    public static final int RESOLUTION = 512;
    private static final int PIXEL_COUNT = RESOLUTION * RESOLUTION;
    private static final double COMPLETION_ROUND_UP_PERCENT = 99.0D;
    private static final int COMPLETION_ROUND_UP_PIXELS = (int)Math.floor(PIXEL_COUNT * COMPLETION_ROUND_UP_PERCENT / 100.0D);
    private static final int DISCOVERED_BYTES = (PIXEL_COUNT + 7) / 8;
    private static final int MAX_SAMPLES_PER_PIXEL_AXIS = 4;
    private static final SavedDataType<GlobeMapSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "globe_map"),
            GlobeMapSavedData::empty,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("version", 1).forGetter(data -> data.version),
                    Codec.INT.fieldOf("tileSizeBlocks").forGetter(data -> data.tileSizeBlocks),
                    TilingMode.CODEC.optionalFieldOf("tilingMode", TilingMode.SQUARE).forGetter(data -> data.tilingMode),
                    Codec.STRING.optionalFieldOf("projection", "legacy-square-xz-v1").forGetter(data -> data.projectionIdentity),
                    Codec.INT.optionalFieldOf("resolution", RESOLUTION).forGetter(data -> data.resolution),
                    Codec.BYTE_BUFFER.fieldOf("discovered").forGetter(data -> ByteBuffer.wrap(data.discovered)),
                    Codec.BYTE_BUFFER.fieldOf("colors").forGetter(data -> ByteBuffer.wrap(data.colors)),
                    Codec.INT.optionalFieldOf("fillCursor", 0).forGetter(data -> data.fillCursor)
            ).apply(instance, GlobeMapSavedData::new)),
            DataFixTypes.SAVED_DATA_MAP_DATA);

    private final int version;
    private final int tileSizeBlocks;
    private final TilingMode tilingMode;
    private final String projectionIdentity;
    private final int resolution;
    private final byte[] discovered;
    private final byte[] colors;
    private int fillCursor;
    private int revision = 1;

    private GlobeMapSavedData() {
        this(
                CURRENT_VERSION,
                RESOLUTION,
                TilingMode.SQUARE,
                "square-v1:32,0:0,32:atlas-ab-v1",
                RESOLUTION,
                new byte[DISCOVERED_BYTES],
                new byte[PIXEL_COUNT],
                0);
    }

    private GlobeMapSavedData(
            final int version,
            final int tileSizeBlocks,
            final TilingMode tilingMode,
            final String projectionIdentity,
            final int resolution,
            final ByteBuffer discovered,
            final ByteBuffer colors,
            final int fillCursor) {
        this(
                version,
                tileSizeBlocks,
                tilingMode,
                projectionIdentity,
                resolution,
                copyBytes(discovered),
                copyBytes(colors),
                fillCursor);
    }

    private GlobeMapSavedData(
            final int version,
            final int tileSizeBlocks,
            final TilingMode tilingMode,
            final String projectionIdentity,
            final int resolution,
            final byte[] discovered,
            final byte[] colors,
            final int fillCursor) {
        this.version = version;
        this.tileSizeBlocks = tileSizeBlocks;
        this.tilingMode = tilingMode;
        this.projectionIdentity = projectionIdentity;
        this.resolution = resolution == RESOLUTION ? RESOLUTION : RESOLUTION;
        this.discovered = normalize(discovered, DISCOVERED_BYTES);
        this.colors = normalize(colors, PIXEL_COUNT);
        this.fillCursor = Mth.clamp(fillCursor, 0, PIXEL_COUNT);
    }

    public static GlobeMapSavedData get(final ServerLevel level, final DimensionTiling tiling) {
        GlobeMapSavedData existing = level.getDataStorage().get(TYPE);
        if (existing != null && existing.matches(tiling)) {
            return existing;
        }

        AtlasTorusProjection projection = AtlasTorusProjection.create(tiling);
        if (existing != null) {
            GlobeWorld.LOGGER.warn(
                    "Resetting Atlas map data because projection {} does not match {}",
                    existing.projectionIdentity,
                    projection.identity());
        }
        GlobeMapSavedData created = new GlobeMapSavedData(
                CURRENT_VERSION,
                tiling.tileSizeBlocks(),
                tiling.mode(),
                projection.identity(),
                RESOLUTION,
                new byte[DISCOVERED_BYTES],
                new byte[PIXEL_COUNT],
                0);
        level.getDataStorage().set(TYPE, created);
        return created;
    }

    public static GlobeMapSavedData getIfPresent(final ServerLevel level, final DimensionTiling tiling) {
        GlobeMapSavedData existing = level.getDataStorage().get(TYPE);
        return existing != null && existing.matches(tiling) ? existing : null;
    }

    public boolean revealAround(
            final ServerLevel level,
            final DimensionTiling tiling,
            final double x,
            final double z,
            final int radiusBlocks,
            final int pixelBudget) {
        if (!Level.OVERWORLD.equals(level.dimension()) || !this.matches(tiling)) {
            return false;
        }
        if (radiusBlocks <= 0 || pixelBudget <= 0) {
            return false;
        }

        AtlasTorusProjection projection = AtlasTorusProjection.create(tiling);
        Vec3 canonicalCenter = projection.geometry().canonicalBlock(new Vec3(x, 0.0D, z));
        AtlasTorusProjection.Pixel centerPixel = projection.pixel(canonicalCenter.x(), canonicalCenter.z(), this.resolution);
        AtlasTorusProjection.PixelRadius pixelRadius = projection.pixelRadius(radiusBlocks, this.resolution);
        double radiusSqr = (double)radiusBlocks * radiusBlocks;
        int sampledPixels = 0;
        boolean changed = false;

        for (int dv = -pixelRadius.v(); dv <= pixelRadius.v(); dv++) {
            int pv = Math.floorMod(centerPixel.v() + dv, this.resolution);
            for (int du = -pixelRadius.u(); du <= pixelRadius.u(); du++) {
                if (sampledPixels >= pixelBudget) {
                    return this.finishReveal(changed);
                }

                int pu = Math.floorMod(centerPixel.u() + du, this.resolution);
                int index = pu + pv * this.resolution;
                if (this.isDiscovered(index)) {
                    continue;
                }

                Vec3 pixelCenter = projection.canonicalPixelCenter(pu, pv, this.resolution);
                if (projection.geometry().wrappedDistanceSqr(canonicalCenter, pixelCenter) > radiusSqr) {
                    continue;
                }

                changed |= this.sampleAndUpdatePixel(level, projection, pu, pv);
                sampledPixels++;
            }
        }

        if (sampledPixels < pixelBudget) {
            int gapRadius = Math.max(pixelRadius.u(), pixelRadius.v()) + 1;
            changed |= this.fillExplorationGaps(level, projection, gapRadius, pixelBudget - sampledPixels);
        }

        return this.finishReveal(changed);
    }

    public boolean refreshColumn(final ServerLevel level, final DimensionTiling tiling, final BlockPos pos) {
        if (!Level.OVERWORLD.equals(level.dimension()) || !this.matches(tiling)) {
            return false;
        }

        AtlasTorusProjection projection = AtlasTorusProjection.create(tiling);
        AtlasTorusProjection.Pixel pixel = projection.pixel(pos.getX(), pos.getZ(), this.resolution);
        if (!this.isDiscovered(pixel.u(), pixel.v())) {
            return false;
        }

        return this.finishReveal(this.sampleAndUpdatePixel(level, projection, pixel.u(), pixel.v()));
    }

    public Identifier dimensionId() {
        return Level.OVERWORLD.identifier();
    }

    public int tileSizeBlocks() {
        return this.tileSizeBlocks;
    }

    public int resolution() {
        return this.resolution;
    }

    public int revision() {
        return 31 * this.revision + this.projectionIdentity.hashCode();
    }

    public byte[] copyDiscovered() {
        return Arrays.copyOf(this.discovered, this.discovered.length);
    }

    public byte[] copyColors() {
        return Arrays.copyOf(this.colors, this.colors.length);
    }

    public double discoveredPercent() {
        int discoveredPixels = this.discoveredPixels();
        return this.isNearComplete(discoveredPixels) ? 100.0D : discoveredPixels * 100.0D / PIXEL_COUNT;
    }

    public int discoveredPixels() {
        int discoveredPixels = 0;
        for (int i = 0; i < PIXEL_COUNT; i++) {
            if (this.isDiscovered(i)) {
                discoveredPixels++;
            }
        }
        return discoveredPixels;
    }

    public int totalPixels() {
        return PIXEL_COUNT;
    }

    public double discoveredAreaBlocks(final DimensionTiling tiling) {
        return this.discoveredPixels() / (double)PIXEL_COUNT
                * AtlasTorusProjection.create(tiling).canonicalBlockArea();
    }

    public boolean complete() {
        return this.isNearComplete(this.discoveredPixels());
    }

    public boolean isDiscoveredCanonicalBlock(final DimensionTiling tiling, final BlockPos pos) {
        if (!this.matches(tiling)) {
            return false;
        }
        if (this.complete()) {
            return true;
        }

        AtlasTorusProjection.Pixel pixel = AtlasTorusProjection.create(tiling)
                .pixel(pos.getX(), pos.getZ(), this.resolution);
        return this.isDiscovered(pixel.u(), pixel.v());
    }

    private static GlobeMapSavedData empty() {
        return new GlobeMapSavedData();
    }

    private boolean matches(final DimensionTiling tiling) {
        if (this.resolution != RESOLUTION || this.tileSizeBlocks != tiling.tileSizeBlocks()) {
            return false;
        }
        if (this.version == 1) {
            return tiling.mode() == TilingMode.SQUARE;
        }
        AtlasTorusProjection projection = AtlasTorusProjection.create(tiling);
        return this.version == CURRENT_VERSION
                && this.tilingMode == tiling.mode()
                && this.projectionIdentity.equals(projection.identity());
    }

    private int samplePixelColor(
            final ServerLevel level,
            final AtlasTorusProjection projection,
            final int pu,
            final int pv) {
        int samples = Mth.clamp(
                (int)Math.ceil(projection.maximumBasisLengthBlocks() / this.resolution),
                1,
                MAX_SAMPLES_PER_PIXEL_AXIS);
        Multiset<MapColor> colorCount = LinkedHashMultiset.create();

        for (int sampleV = 0; sampleV < samples; sampleV++) {
            for (int sampleU = 0; sampleU < samples; sampleU++) {
                Vec3 sample = projection.canonicalPixelSample(
                        pu,
                        pv,
                        this.resolution,
                        (sampleU + 0.5D) / samples,
                        (sampleV + 0.5D) / samples);
                MapColor color = this.sampleColumnColor(level, Mth.floor(sample.x()), Mth.floor(sample.z()));
                if (color != null && color != MapColor.NONE) {
                    colorCount.add(color);
                }
            }
        }

        if (colorCount.isEmpty()) {
            return -1;
        }

        MapColor color = Iterables.getFirst(Multisets.copyHighestCountFirst(colorCount), MapColor.NONE);
        return color.getPackedId(color == MapColor.WATER ? MapColor.Brightness.HIGH : MapColor.Brightness.NORMAL) & 0xFF;
    }

    private boolean sampleAndUpdatePixel(
            final ServerLevel level,
            final AtlasTorusProjection projection,
            final int pu,
            final int pv) {
        int sampledColor = this.samplePixelColor(level, projection, pu, pv);
        return this.updatePixel(pu, pv, sampledColor < 0 ? MapColor.NONE.getPackedId(MapColor.Brightness.NORMAL) : (byte)sampledColor);
    }

    private boolean fillExplorationGaps(
            final ServerLevel level,
            final AtlasTorusProjection projection,
            final int visibilityPixelRadius,
            final int pixelBudget) {
        int discoveredPixels = this.discoveredPixels();
        if (this.isNearComplete(discoveredPixels)) {
            return this.fillAllRemainingPixels(level, projection);
        }
        if (pixelBudget <= 0 || visibilityPixelRadius <= 0) {
            return false;
        }

        int maxAxisSpan = Math.min(this.resolution, visibilityPixelRadius * 2 + 1);
        int maxGapPixels = Math.min(PIXEL_COUNT, maxAxisSpan * maxAxisSpan);
        boolean[] visited = new boolean[PIXEL_COUNT];
        int[] queue = new int[PIXEL_COUNT];
        int[] component = new int[PIXEL_COUNT];
        int[] xMarks = new int[this.resolution];
        int[] zMarks = new int[this.resolution];
        int mark = 1;
        int filledPixels = 0;
        boolean changed = false;

        for (int start = 0; start < PIXEL_COUNT; start++) {
            if (visited[start] || this.isDiscovered(start)) {
                continue;
            }

            int componentSize = this.collectUndiscoveredComponent(start, visited, queue, component, xMarks, zMarks, mark);
            boolean fillable = componentSize <= maxGapPixels
                    && this.circularMarkedSpan(xMarks, mark) <= maxAxisSpan
                    && this.circularMarkedSpan(zMarks, mark) <= maxAxisSpan;
            mark++;

            if (!fillable) {
                continue;
            }
            if (filledPixels + componentSize > pixelBudget) {
                return changed;
            }

            for (int i = 0; i < componentSize; i++) {
                int index = component[i];
                int pu = index % this.resolution;
                int pv = index / this.resolution;
                changed |= this.sampleAndUpdatePixel(level, projection, pu, pv);
                filledPixels++;
            }
            discoveredPixels += componentSize;

            if (this.isNearComplete(discoveredPixels)) {
                changed |= this.fillAllRemainingPixels(level, projection);
                return changed;
            }
        }

        return changed;
    }

    private boolean fillAllRemainingPixels(
            final ServerLevel level,
            final AtlasTorusProjection projection) {
        boolean changed = false;
        for (int index = 0; index < PIXEL_COUNT; index++) {
            if (!this.isDiscovered(index)) {
                int pu = index % this.resolution;
                int pv = index / this.resolution;
                changed |= this.sampleAndUpdatePixel(level, projection, pu, pv);
            }
        }
        return changed;
    }

    private int collectUndiscoveredComponent(
            final int start,
            final boolean[] visited,
            final int[] queue,
            final int[] component,
            final int[] xMarks,
            final int[] zMarks,
            final int mark) {
        int head = 0;
        int tail = 0;
        int componentSize = 0;
        visited[start] = true;
        queue[tail++] = start;

        while (head < tail) {
            int index = queue[head++];
            component[componentSize++] = index;
            int px = index % this.resolution;
            int pz = index / this.resolution;
            xMarks[px] = mark;
            zMarks[pz] = mark;

            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }

                    int nx = Math.floorMod(px + dx, this.resolution);
                    int nz = Math.floorMod(pz + dz, this.resolution);
                    int neighbor = nx + nz * this.resolution;
                    if (visited[neighbor] || this.isDiscovered(neighbor)) {
                        continue;
                    }

                    visited[neighbor] = true;
                    queue[tail++] = neighbor;
                }
            }
        }

        return componentSize;
    }

    private int circularMarkedSpan(final int[] marks, final int mark) {
        int first = -1;
        int previous = -1;
        int markedCount = 0;
        int largestGap = 0;
        for (int i = 0; i < marks.length; i++) {
            if (marks[i] != mark) {
                continue;
            }

            if (first < 0) {
                first = i;
            } else {
                largestGap = Math.max(largestGap, i - previous - 1);
            }
            previous = i;
            markedCount++;
        }

        if (markedCount == 0) {
            return 0;
        }
        if (markedCount == marks.length) {
            return marks.length;
        }

        largestGap = Math.max(largestGap, first + marks.length - previous - 1);
        return marks.length - largestGap;
    }

    private boolean isNearComplete(final int discoveredPixels) {
        return discoveredPixels >= COMPLETION_ROUND_UP_PIXELS;
    }

    private MapColor sampleColumnColor(final ServerLevel level, final int x, final int z) {
        int sectionX = SectionPos.blockToSectionCoord(x);
        int sectionZ = SectionPos.blockToSectionCoord(z);
        LevelChunk chunk = level.getChunk(sectionX, sectionZ);
        if (chunk == null || chunk.isEmpty()) {
            return null;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        int columnY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
        BlockState state;
        if (columnY <= level.getMinY()) {
            state = Blocks.BEDROCK.defaultBlockState();
        } else {
            do {
                pos.setY(--columnY);
                state = chunk.getBlockState(pos);
            } while (state.getMapColor(level, pos) == MapColor.NONE && columnY > level.getMinY());

            if (columnY > level.getMinY()) {
                state = this.correctStateForFluidBlock(level, state, pos);
            }
        }

        return state.getMapColor(level, pos);
    }

    private BlockState correctStateForFluidBlock(final ServerLevel level, final BlockState state, final BlockPos pos) {
        FluidState fluidState = state.getFluidState();
        return !fluidState.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP) ? fluidState.createLegacyBlock() : state;
    }

    private boolean updatePixel(final int px, final int pz, final byte color) {
        int index = px + pz * this.resolution;
        boolean wasDiscovered = this.isDiscovered(index);
        byte previousColor = this.colors[index];
        this.setDiscovered(index);
        this.colors[index] = color;
        return !wasDiscovered || previousColor != color;
    }

    private boolean finishReveal(final boolean changed) {
        if (changed) {
            this.revision++;
            this.setDirty();
        }
        return changed;
    }

    private boolean isDiscovered(final int index) {
        return (this.discovered[index >> 3] & (1 << (index & 7))) != 0;
    }

    private boolean isDiscovered(final int px, final int pz) {
        return this.isDiscovered(px + pz * this.resolution);
    }

    private void setDiscovered(final int index) {
        this.discovered[index >> 3] = (byte)(this.discovered[index >> 3] | (1 << (index & 7)));
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
