package globe.world.network;

import globe.world.config.TilingSettings;
import globe.world.config.TilingSettingsHolder;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

import java.util.List;
import java.util.function.Consumer;

public final class GlobeWorldNetworking {
    private static final ConfigurationTask.Type SETTINGS_SYNC_TASK = new ConfigurationTask.Type(
            GlobeWorldSettingsPayload.TYPE.id().toString());
    private static final Component CLIENT_REQUIRED_MESSAGE = Component.literal(
            "This server requires the Globe World client mod.");

    private GlobeWorldNetworking() {
    }

    public static void registerCommon() {
        PayloadTypeRegistry.clientboundConfiguration().register(GlobeWorldSettingsPayload.TYPE, GlobeWorldSettingsPayload.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(GlobeWorldSettingsAckPayload.TYPE, GlobeWorldSettingsAckPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GlobeWorldSettingsPayload.TYPE, GlobeWorldSettingsPayload.CODEC);

        ServerConfigurationNetworking.registerGlobalReceiver(GlobeWorldSettingsAckPayload.TYPE, (payload, context) ->
                ((FabricServerConfigurationPacketListenerImpl) context.packetListener()).completeTask(SETTINGS_SYNC_TASK));

        ServerConfigurationConnectionEvents.CONFIGURE.register(GlobeWorldNetworking::configureJoiningClient);
    }

    public static void broadcastSettings(MinecraftServer server, TilingSettings settings) {
        TilingSettings sanitized = settings.sanitized();
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (ServerPlayNetworking.canSend(player, GlobeWorldSettingsPayload.TYPE)) {
                ServerPlayNetworking.send(player, new GlobeWorldSettingsPayload(sanitized));
            } else if (requiresClient(sanitized)) {
                player.connection.disconnect(CLIENT_REQUIRED_MESSAGE);
            }
        }
    }

    private static void configureJoiningClient(ServerConfigurationPacketListenerImpl listener, MinecraftServer server) {
        TilingSettings settings = ((TilingSettingsHolder) (Object) server.getWorldGenSettings())
                .globeWorld$getTilingSettings()
                .sanitized();
        if (!ServerConfigurationNetworking.canSend(listener, GlobeWorldSettingsPayload.TYPE)) {
            if (requiresClient(settings)) {
                listener.disconnect(CLIENT_REQUIRED_MESSAGE);
            }
            return;
        }

        ((FabricServerConfigurationPacketListenerImpl) listener).addTask(new SettingsSyncTask(settings));
    }

    private static boolean requiresClient(TilingSettings settings) {
        return settings.enabled() || settings.netherEnabled();
    }

    private record SettingsSyncTask(TilingSettings settings) implements ConfigurationTask {
        @Override
        public void start(Consumer<Packet<?>> sender) {
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(new GlobeWorldSettingsPayload(settings)));
        }

        @Override
        public Type type() {
            return SETTINGS_SYNC_TASK;
        }
    }
}
