package globe.world.map;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import globe.world.GlobeWorld;
import globe.world.util.CoordUtil;
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

        double canonicalX = CoordUtil.wrapBlock(tiling, x);
        double canonicalZ = CoordUtil.wrapBlock(tiling, z);
        int centerPixelX = this.pixelForCanonicalBlock(canonicalX);
        int centerPixelZ = this.pixelForCanonicalBlock(canonicalZ);
        int pixelRadius = Math.min((int)Math.ceil(radiusBlocks * (double)this.resolution / this.tileSizeBlocks) + 1, this.resolution / 2);
        double radiusSqr = (double)radiusBlocks * radiusBlocks;
        int sampledPixels = 0;
        boolean changed = false;

        for (int dz = -pixelRadius; dz <= pixelRadius; dz++) {
            int pz = Math.floorMod(centerPixelZ + dz, this.resolution);
            double pixelZ = this.canonicalBlockCenter(pz);
            double blockDz = CoordUtil.wrappedDeltaBlock(tiling, pixelZ, canonicalZ);
            for (int dx = -pixelRadius; dx <= pixelRadius; dx++) {
                if (sampledPixels >= pixelBudget) {
                    return this.finishReveal(changed);
                }

                int px = Math.floorMod(centerPixelX + dx, this.resolution);
                int index = px + pz * this.resolution;
                if (this.isDiscovered(index)) {
                    continue;
                }

                double pixelX = this.canonicalBlockCenter(px);
                double blockDx = CoordUtil.wrappedDeltaBlock(tiling, pixelX, canonicalX);
                if (blockDx * blockDx + blockDz * blockDz > radiusSqr) {
                    continue;
                }

                changed |= this.sampleAndUpdatePixel(level, px, pz);
                sampledPixels++;
            }
        }

        if (sampledPixels < pixelBudget) {
            changed |= this.fillSmallGapsAround(level, centerPixelX, centerPixelZ, pixelRadius + 1, pixelBudget - sampledPixels);
        }

        return this.finishReveal(changed);
    }

    public boolean refreshColumn(final ServerLevel level, final DimensionTiling tiling, final BlockPos pos) {
        if (!Level.OVERWORLD.equals(level.dimension()) || !this.matches(tiling)) {
            return false;
        }

        int px = this.pixelForCanonicalBlock(CoordUtil.wrapBlock(tiling, pos.getX()));
        int pz = this.pixelForCanonicalBlock(CoordUtil.wrapBlock(tiling, pos.getZ()));
        if (!this.isDiscovered(px, pz)) {
            return false;
        }

        return this.finishReveal(this.sampleAndUpdatePixel(level, px, pz));
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

    private double canonicalBlockCenter(final int pixel) {
        double normalized = (pixel + 0.5D) / this.resolution;
        return normalized * this.tileSizeBlocks - this.tileSizeBlocks / 2.0D;
    }

    private int pixelForCanonicalBlock(final double block) {
        double normalized = (block + this.tileSizeBlocks / 2.0D) / this.tileSizeBlocks;
        return Math.floorMod(Mth.floor(normalized * this.resolution), this.resolution);
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

    private boolean sampleAndUpdatePixel(final ServerLevel level, final int px, final int pz) {
        int sampledColor = this.samplePixelColor(level, px, pz);
        return this.updatePixel(px, pz, sampledColor < 0 ? MapColor.NONE.getPackedId(MapColor.Brightness.NORMAL) : (byte)sampledColor);
    }

    private boolean fillSmallGapsAround(
            final ServerLevel level,
            final int centerPixelX,
            final int centerPixelZ,
            final int pixelRadius,
            final int pixelBudget) {
        int searchRadius = Math.min(pixelRadius, this.resolution / 2);
        int filledPixels = 0;
        boolean changed = false;

        for (int dz = -searchRadius; dz <= searchRadius; dz++) {
            int pz = Math.floorMod(centerPixelZ + dz, this.resolution);
            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                if (filledPixels >= pixelBudget) {
                    return changed;
                }

                int px = Math.floorMod(centerPixelX + dx, this.resolution);
                if (this.isDiscovered(px, pz) || !this.isSmallGap(px, pz)) {
                    continue;
                }

                changed |= this.sampleAndUpdatePixel(level, px, pz);
                filledPixels++;
            }
        }

        return changed;
    }

    private boolean isSmallGap(final int px, final int pz) {
        int discoveredNeighbors = 0;
        int cardinalNeighbors = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                if (this.isDiscovered(Math.floorMod(px + dx, this.resolution), Math.floorMod(pz + dz, this.resolution))) {
                    discoveredNeighbors++;
                    if (dx == 0 || dz == 0) {
                        cardinalNeighbors++;
                    }
                }
            }
        }

        return discoveredNeighbors >= 7 && cardinalNeighbors >= 3;
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
