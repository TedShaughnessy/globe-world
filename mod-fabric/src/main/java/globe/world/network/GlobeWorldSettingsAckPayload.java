package globe.world.network;

import globe.world.GlobeWorld;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record GlobeWorldSettingsAckPayload() implements CustomPacketPayload {
    public static final GlobeWorldSettingsAckPayload INSTANCE = new GlobeWorldSettingsAckPayload();
    public static final Type<GlobeWorldSettingsAckPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "settings_ack"));
    public static final StreamCodec<FriendlyByteBuf, GlobeWorldSettingsAckPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
