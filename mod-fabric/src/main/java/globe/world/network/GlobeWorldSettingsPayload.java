package globe.world.network;

import globe.world.GlobeWorld;
import globe.world.config.GlobeSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record GlobeWorldSettingsPayload(GlobeSettings settings) implements CustomPacketPayload {
    public static final Type<GlobeWorldSettingsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "settings"));
    public static final StreamCodec<FriendlyByteBuf, GlobeWorldSettingsPayload> CODEC =
            ByteBufCodecs.fromCodec(GlobeSettings.CODEC)
                    .map(GlobeWorldSettingsPayload::new, GlobeWorldSettingsPayload::settings)
                    .mapStream(buffer -> buffer);

    public GlobeWorldSettingsPayload {
        settings = settings == null ? GlobeSettings.DEFAULT : settings;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
