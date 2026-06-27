package globe.world.network;

import globe.world.GlobeWorld;
import globe.world.atlas.GlobeAtlasLoadout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record GlobeAtlasUpdatePayload(BlockPos pos, GlobeAtlasLoadout loadout) implements CustomPacketPayload {
    public static final Type<GlobeAtlasUpdatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "atlas_update"));
    public static final StreamCodec<FriendlyByteBuf, GlobeAtlasUpdatePayload> CODEC = StreamCodec.ofMember(
            GlobeAtlasUpdatePayload::write,
            GlobeAtlasUpdatePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static GlobeAtlasUpdatePayload read(final FriendlyByteBuf input) {
        return new GlobeAtlasUpdatePayload(input.readBlockPos(), GlobeAtlasLoadout.STREAM_CODEC.decode(input));
    }

    private void write(final FriendlyByteBuf output) {
        output.writeBlockPos(this.pos);
        GlobeAtlasLoadout.STREAM_CODEC.encode(output, this.loadout);
    }
}
