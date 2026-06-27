package globe.world.network;

import globe.world.GlobeWorld;
import globe.world.map.GlobeMapSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record GlobeMapSnapshotPayload(
        Identifier dimension,
        int tileSizeBlocks,
        int resolution,
        int revision,
        byte[] discovered,
        byte[] colors) implements CustomPacketPayload {
    private static final int MAX_TEXTURE_BYTES = GlobeMapSavedData.RESOLUTION * GlobeMapSavedData.RESOLUTION;
    private static final int MAX_DISCOVERED_BYTES = (MAX_TEXTURE_BYTES + 7) / 8;

    public static final Type<GlobeMapSnapshotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "globe_map_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, GlobeMapSnapshotPayload> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC.mapStream(buffer -> buffer),
            GlobeMapSnapshotPayload::dimension,
            ByteBufCodecs.VAR_INT.mapStream(buffer -> buffer),
            GlobeMapSnapshotPayload::tileSizeBlocks,
            ByteBufCodecs.VAR_INT.mapStream(buffer -> buffer),
            GlobeMapSnapshotPayload::resolution,
            ByteBufCodecs.VAR_INT.mapStream(buffer -> buffer),
            GlobeMapSnapshotPayload::revision,
            ByteBufCodecs.byteArray(MAX_DISCOVERED_BYTES).mapStream(buffer -> buffer),
            GlobeMapSnapshotPayload::discovered,
            ByteBufCodecs.byteArray(MAX_TEXTURE_BYTES).mapStream(buffer -> buffer),
            GlobeMapSnapshotPayload::colors,
            GlobeMapSnapshotPayload::new);

    public GlobeMapSnapshotPayload {
        discovered = discovered == null ? new byte[0] : discovered;
        colors = colors == null ? new byte[0] : colors;
    }

    public static GlobeMapSnapshotPayload from(final GlobeMapSavedData data) {
        return new GlobeMapSnapshotPayload(
                data.dimensionId(),
                data.tileSizeBlocks(),
                data.resolution(),
                data.revision(),
                data.copyDiscovered(),
                data.copyColors());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
