package globe.world.network;

import globe.world.GlobeWorld;
import globe.world.atlas.GlobeAtlasLoadout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record GlobeAtlasUpdatePayload(
        BlockPos pos,
        GlobeAtlasLoadout loadout,
        String name,
        boolean projectionEnabled) implements CustomPacketPayload {
    public static final Type<GlobeAtlasUpdatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "atlas_update"));
    public static final StreamCodec<FriendlyByteBuf, GlobeAtlasUpdatePayload> CODEC = StreamCodec.ofMember(
            GlobeAtlasUpdatePayload::write,
            GlobeAtlasUpdatePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public GlobeAtlasUpdatePayload {
        if (name == null) {
            name = "";
        } else if (name.length() > 64) {
            name = name.substring(0, 64);
        }
    }

    private static GlobeAtlasUpdatePayload read(final FriendlyByteBuf input) {
        return new GlobeAtlasUpdatePayload(
                input.readBlockPos(),
                GlobeAtlasLoadout.STREAM_CODEC.decode(input),
                input.readUtf(64),
                input.readBoolean());
    }

    private void write(final FriendlyByteBuf output) {
        output.writeBlockPos(this.pos);
        GlobeAtlasLoadout.STREAM_CODEC.encode(output, this.loadout);
        output.writeUtf(this.name, 64);
        output.writeBoolean(this.projectionEnabled);
    }
}
