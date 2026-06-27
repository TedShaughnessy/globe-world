package globe.world.map;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import globe.world.GlobeWorld;
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

import java.nio.ByteBuffer;
import java.util.Arrays;

public class GlobeMapSavedData extends SavedData {
    public static final int CURRENT_VERSION = 1;
    public static final int RESOLUTION = 512;
    private static final int PIXEL_COUNT = RESOLUTION * RESOLUTION;
    private static final int DISCOVERED_BYTES = (PIXEL_COUNT + 7) / 8;
    private static final int MAX_SAMPLES_PER_PIXEL_AXIS = 4;
    private static final SavedDataType<GlobeMapSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "globe_map"),
            GlobeMapSavedData::empty,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("version", CURRENT_VERSION).forGetter(data -> data.version),
                    Codec.INT.fieldOf("tileSizeBlocks").forGetter(data -> data.tileSizeBlocks),
                    Codec.INT.optionalFieldOf("resolution", RESOLUTION).forGetter(data -> data.resolution),
                    Codec.BYTE_BUFFER.fieldOf("discovered").forGetter(data -> ByteBuffer.wrap(data.discovered)),
                    Codec.BYTE_BUFFER.fieldOf("colors").forGetter(data -> ByteBuffer.wrap(data.colors)),
                    Codec.INT.optionalFieldOf("fillCursor", 0).forGetter(data -> data.fillCursor)
            ).apply(instance, GlobeMapSavedData::new)),
            DataFixTypes.SAVED_DATA_MAP_DATA);

    private final int version;
    private final int tileSizeBlocks;
    private final int resolution;
    private final byte[] discovered;
    private final byte[] colors;
    private int fillCursor;
    private int revision = 1;

    private GlobeMapSavedData() {
        this(CURRENT_VERSION, RESOLUTION, RESOLUTION, new byte[DISCOVERED_BYTES], new byte[PIXEL_COUNT], 0);
    }

    private GlobeMapSavedData(
            final int version,
            final int tileSizeBlocks,
            final int resolution,
            final ByteBuffer discovered,
            final ByteBuffer colors,
            final int fillCursor) {
        this(version, tileSizeBlocks, resolution, copyBytes(discovered), copyBytes(colors), fillCursor);
    }

    private GlobeMapSavedData(
            final int version,
            final int tileSizeBlocks,
            final int resolution,
            final byte[] discovered,
            final byte[] colors,
            final int fillCursor) {
        this.version = version;
        this.tileSizeBlocks = tileSizeBlocks;
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

        GlobeMapSavedData created = new GlobeMapSavedData(CURRENT_VERSION, tiling.tileSizeBlocks(), RESOLUTION, new byte[DISCOVERED_BYTES], new byte[PIXEL_COUNT], 0);
        level.getDataStorage().set(TYPE, created);
        return created;
    }

    public boolean fillNextPixels(final ServerLevel level, final DimensionTiling tiling, final int pixelBudget) {
        if (!Level.OVERWORLD.equals(level.dimension()) || !this.matches(tiling)) {
            return false;
        }
        if (pixelBudget <= 0 || this.fillCursor >= PIXEL_COUNT) {
            return false;
        }

        boolean changed = false;
        int end = Math.min(PIXEL_COUNT, this.fillCursor + pixelBudget);
        while (this.fillCursor < end) {
            int index = this.fillCursor++;
            int px = index % this.resolution;
            int pz = index / this.resolution;
            int sampledColor = this.samplePixelColor(level, px, pz);
            changed |= this.updatePixel(px, pz, sampledColor < 0 ? MapColor.NONE.getPackedId(MapColor.Brightness.NORMAL) : (byte)sampledColor);
        }

        if (changed) {
            this.revision++;
            this.setDirty();
        }
        return changed;
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
        return this.revision;
    }

    public byte[] copyDiscovered() {
        return Arrays.copyOf(this.discovered, this.discovered.length);
    }

    public byte[] copyColors() {
        return Arrays.copyOf(this.colors, this.colors.length);
    }

    public double discoveredPercent() {
        int discoveredPixels = 0;
        for (int i = 0; i < PIXEL_COUNT; i++) {
            if (this.isDiscovered(i)) {
                discoveredPixels++;
            }
        }
        return discoveredPixels * 100.0D / PIXEL_COUNT;
    }

    private static GlobeMapSavedData empty() {
        return new GlobeMapSavedData();
    }

    private boolean matches(final DimensionTiling tiling) {
        return this.version == CURRENT_VERSION
                && this.resolution == RESOLUTION
                && this.tileSizeBlocks == tiling.tileSizeBlocks();
    }

    private int canonicalBlock(final int pixel) {
        double normalized = (pixel + 0.5D) / this.resolution;
        return Mth.floor(normalized * this.tileSizeBlocks - this.tileSizeBlocks / 2.0D);
    }

    private int samplePixelColor(final ServerLevel level, final int px, final int pz) {
        int blockX = this.canonicalBlock(px);
        int blockZ = this.canonicalBlock(pz);
        int pixelBlockSize = Math.max(1, Mth.positiveCeilDiv(this.tileSizeBlocks, this.resolution));
        int step = Math.max(1, Mth.positiveCeilDiv(pixelBlockSize, MAX_SAMPLES_PER_PIXEL_AXIS));
        Multiset<MapColor> colorCount = LinkedHashMultiset.create();

        for (int dx = 0; dx < pixelBlockSize; dx += step) {
            for (int dz = 0; dz < pixelBlockSize; dz += step) {
                MapColor color = this.sampleColumnColor(
                        level,
                        this.wrapCanonicalBlock(blockX + dx),
                        this.wrapCanonicalBlock(blockZ + dz));
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

    private int wrapCanonicalBlock(final int block) {
        int half = this.tileSizeBlocks / 2;
        return Math.floorMod(block + half, this.tileSizeBlocks) - half;
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

    private boolean isDiscovered(final int index) {
        return (this.discovered[index >> 3] & (1 << (index & 7))) != 0;
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
