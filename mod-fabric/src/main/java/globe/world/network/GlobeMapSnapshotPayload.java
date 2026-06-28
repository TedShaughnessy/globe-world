package globe.world.network;

import globe.world.GlobeWorld;
import globe.world.atlas.GlobeAtlasSurvey;
import globe.world.atlas.GlobeAtlasSurveyState;
import globe.world.atlas.GlobeDiscoveryRewards;
import globe.world.map.GlobeMapSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record GlobeMapSnapshotPayload(
        Identifier dimension,
        int tileSizeBlocks,
        int resolution,
        int revision,
        byte[] discovered,
        byte[] colors,
        boolean surveyMode,
        int biomesVisited,
        int visitedCells,
        int totalCells,
        byte[] visitedCellBits) implements CustomPacketPayload {
    private static final int MAX_TEXTURE_BYTES = GlobeMapSavedData.RESOLUTION * GlobeMapSavedData.RESOLUTION;
    private static final int MAX_DISCOVERED_BYTES = (MAX_TEXTURE_BYTES + 7) / 8;
    private static final int MAX_VISITED_CELL_BYTES = (GlobeAtlasSurvey.COVERAGE_CELL_COUNT + 7) / 8;

    public static final Type<GlobeMapSnapshotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "globe_map_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, GlobeMapSnapshotPayload> CODEC = StreamCodec.ofMember(
            GlobeMapSnapshotPayload::write,
            GlobeMapSnapshotPayload::read);

    public GlobeMapSnapshotPayload {
        discovered = discovered == null ? new byte[0] : discovered;
        colors = colors == null ? new byte[0] : colors;
        visitedCellBits = visitedCellBits == null ? new byte[0] : visitedCellBits;
    }

    public static GlobeMapSnapshotPayload from(final GlobeMapSavedData data) {
        return from(data, null, GlobeDiscoveryRewards.EMPTY);
    }

    public static GlobeMapSnapshotPayload from(
            final GlobeMapSavedData data,
            final GlobeAtlasSurveyState survey,
            final GlobeDiscoveryRewards rewards) {
        boolean surveyMode = survey != null && rewards.surveyMode();
        return new GlobeMapSnapshotPayload(
                data.dimensionId(),
                data.tileSizeBlocks(),
                data.resolution(),
                data.revision(),
                data.copyDiscovered(),
                data.copyColors(),
                surveyMode,
                rewards.biomesVisited(),
                rewards.visitedCells(),
                rewards.totalCells(),
                surveyMode ? survey.copyVisitedCells() : new byte[0]);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static GlobeMapSnapshotPayload read(final FriendlyByteBuf input) {
        Identifier dimension = Identifier.STREAM_CODEC.decode(input);
        int tileSizeBlocks = input.readVarInt();
        int resolution = input.readVarInt();
        int revision = input.readVarInt();
        byte[] discovered = input.readByteArray(MAX_DISCOVERED_BYTES);
        byte[] colors = input.readByteArray(MAX_TEXTURE_BYTES);
        boolean surveyMode = input.readBoolean();
        int biomesVisited = input.readVarInt();
        int visitedCells = input.readVarInt();
        int totalCells = input.readVarInt();
        byte[] visitedCellBits = input.readByteArray(MAX_VISITED_CELL_BYTES);
        return new GlobeMapSnapshotPayload(
                dimension,
                tileSizeBlocks,
                resolution,
                revision,
                discovered,
                colors,
                surveyMode,
                biomesVisited,
                visitedCells,
                totalCells,
                visitedCellBits);
    }

    private void write(final FriendlyByteBuf output) {
        Identifier.STREAM_CODEC.encode(output, this.dimension);
        output.writeVarInt(this.tileSizeBlocks);
        output.writeVarInt(this.resolution);
        output.writeVarInt(this.revision);
        output.writeByteArray(this.discovered);
        output.writeByteArray(this.colors);
        output.writeBoolean(this.surveyMode);
        output.writeVarInt(this.biomesVisited);
        output.writeVarInt(this.visitedCells);
        output.writeVarInt(this.totalCells);
        output.writeByteArray(this.visitedCellBits);
    }
}
