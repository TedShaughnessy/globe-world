package globe.world.network;

import globe.world.GlobeWorld;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record GlobeAtlasSurveyWindowPayload(
        Identifier dimension,
        int centerChunkX,
        int centerChunkZ,
        int windowChunks,
        int revision,
        byte[] discovered,
        List<Identifier> biomePalette,
        byte[] biomeIndexes,
        List<Marker> markers) implements CustomPacketPayload {
    public static final int HELD_WINDOW_CHUNKS = 128;
    public static final int PLACED_WINDOW_CHUNKS = 512;
    public static final int MAX_WINDOW_CHUNKS = PLACED_WINDOW_CHUNKS;
    private static final int MAX_CELLS = MAX_WINDOW_CHUNKS * MAX_WINDOW_CHUNKS;
    private static final int MAX_DISCOVERED_BYTES = (MAX_CELLS + 7) / 8;
    private static final int MAX_PALETTE = 255;
    private static final int MAX_MARKERS = 128;

    public static final Type<GlobeAtlasSurveyWindowPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "atlas_survey_window"));
    public static final StreamCodec<FriendlyByteBuf, GlobeAtlasSurveyWindowPayload> CODEC = StreamCodec.ofMember(
            GlobeAtlasSurveyWindowPayload::write,
            GlobeAtlasSurveyWindowPayload::read);

    public GlobeAtlasSurveyWindowPayload {
        discovered = discovered == null ? new byte[0] : discovered.clone();
        biomePalette = biomePalette == null ? List.of() : List.copyOf(biomePalette);
        biomeIndexes = biomeIndexes == null ? new byte[0] : biomeIndexes.clone();
        markers = markers == null ? List.of() : List.copyOf(markers);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static GlobeAtlasSurveyWindowPayload read(final FriendlyByteBuf input) {
        Identifier dimension = Identifier.STREAM_CODEC.decode(input);
        int centerChunkX = input.readVarInt();
        int centerChunkZ = input.readVarInt();
        int windowChunks = input.readVarInt();
        int revision = input.readVarInt();
        byte[] discovered = input.readByteArray(MAX_DISCOVERED_BYTES);
        int paletteCount = readBoundedCount(input, MAX_PALETTE, "biome palette");
        List<Identifier> palette = new ArrayList<>(paletteCount);
        for (int i = 0; i < paletteCount; i++) {
            palette.add(Identifier.STREAM_CODEC.decode(input));
        }
        byte[] biomeIndexes = input.readByteArray(MAX_CELLS);
        int markerCount = readBoundedCount(input, MAX_MARKERS, "marker");
        List<Marker> markers = new ArrayList<>(markerCount);
        for (int i = 0; i < markerCount; i++) {
            markers.add(Marker.read(input));
        }
        return new GlobeAtlasSurveyWindowPayload(
                dimension,
                centerChunkX,
                centerChunkZ,
                windowChunks,
                revision,
                discovered,
                palette,
                biomeIndexes,
                markers);
    }

    private void write(final FriendlyByteBuf output) {
        Identifier.STREAM_CODEC.encode(output, this.dimension);
        output.writeVarInt(this.centerChunkX);
        output.writeVarInt(this.centerChunkZ);
        output.writeVarInt(this.windowChunks);
        output.writeVarInt(this.revision);
        output.writeByteArray(this.discovered);
        output.writeVarInt(Math.min(this.biomePalette.size(), MAX_PALETTE));
        for (int i = 0; i < this.biomePalette.size() && i < MAX_PALETTE; i++) {
            Identifier.STREAM_CODEC.encode(output, this.biomePalette.get(i));
        }
        output.writeByteArray(this.biomeIndexes);
        output.writeVarInt(Math.min(this.markers.size(), MAX_MARKERS));
        for (int i = 0; i < this.markers.size() && i < MAX_MARKERS; i++) {
            this.markers.get(i).write(output);
        }
    }

    private static int readBoundedCount(final FriendlyByteBuf input, final int max, final String label) {
        int count = input.readVarInt();
        if (count < 0 || count > max) {
            throw new DecoderException("Globe atlas survey window " + label + " count " + count + " exceeds limit " + max);
        }
        return count;
    }

    public record Marker(int x, int z, int color) {
        private static Marker read(final FriendlyByteBuf input) {
            return new Marker(input.readVarInt(), input.readVarInt(), input.readInt());
        }

        private void write(final FriendlyByteBuf output) {
            output.writeVarInt(this.x);
            output.writeVarInt(this.z);
            output.writeInt(this.color);
        }
    }
}
