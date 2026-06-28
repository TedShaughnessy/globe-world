package globe.world.network;

import globe.world.GlobeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record GlobeAtlasTravelPayload(BlockPos source, BlockPos destination) implements CustomPacketPayload {
    public static final Type<GlobeAtlasTravelPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "atlas_travel"));
    public static final StreamCodec<FriendlyByteBuf, GlobeAtlasTravelPayload> CODEC = StreamCodec.ofMember(
            GlobeAtlasTravelPayload::write,
            GlobeAtlasTravelPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static GlobeAtlasTravelPayload read(final FriendlyByteBuf input) {
        return new GlobeAtlasTravelPayload(input.readBlockPos(), input.readBlockPos());
    }

    private void write(final FriendlyByteBuf output) {
        output.writeBlockPos(this.source);
        output.writeBlockPos(this.destination);
    }
}
