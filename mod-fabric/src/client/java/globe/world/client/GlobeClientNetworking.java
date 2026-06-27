package globe.world.client;

import globe.world.config.GlobeSettings;
import globe.world.client.render.GlobeMapTextureCache;
import globe.world.network.GlobeEntityAliasCommandPayload;
import globe.world.network.GlobeMapSnapshotPayload;
import globe.world.network.GlobeWorldSettingsAckPayload;
import globe.world.network.GlobeWorldSettingsPayload;
import globe.world.util.GlobeEntityAliasMode;
import globe.world.util.GlobeEntityAliasing;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class GlobeClientNetworking {
    private GlobeClientNetworking() {
    }

    public static void register() {
        ClientConfigurationNetworking.registerGlobalReceiver(GlobeWorldSettingsPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    GlobeClientSettings.applySyncedFromServer(payload.settings());
                    ClientConfigurationNetworking.send(GlobeWorldSettingsAckPayload.INSTANCE);
                }));

        ClientPlayNetworking.registerGlobalReceiver(GlobeWorldSettingsPayload.TYPE, (payload, context) ->
                GlobeClientSettings.applySyncedFromServer(payload.settings()));

        ClientPlayNetworking.registerGlobalReceiver(GlobeEntityAliasCommandPayload.TYPE, (payload, context) ->
                context.client().execute(() -> handleEntityAliasCommand(payload.action())));

        ClientPlayNetworking.registerGlobalReceiver(GlobeMapSnapshotPayload.TYPE, (payload, context) ->
                context.client().execute(() -> GlobeMapTextureCache.applySnapshot(payload)));

        ClientConfigurationConnectionEvents.DISCONNECT.register((listener, client) -> resetSyncedSettings());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> resetSyncedSettings());
    }

    private static void handleEntityAliasCommand(GlobeEntityAliasCommandPayload.Action action) {
        switch (action) {
            case SHOW -> sendEntityAliasState();
            case CYCLE_MODE -> {
                GlobeEntityAliasMode mode = GlobeEntityAliasing.cycleMode();
                sendFeedback("Globe entity aliases: " + mode.displayName());
            }
            case CYCLE_RINGS -> {
                GlobeEntityAliasing.cycleMaxAliasRings();
                sendFeedback("Globe entity alias rings: " + GlobeEntityAliasing.maxAliasRingsDisplayName());
            }
        }
    }

    private static void sendEntityAliasState() {
        sendFeedback("Globe entity aliases: "
                + GlobeEntityAliasing.mode().displayName()
                + ", rings "
                + GlobeEntityAliasing.maxAliasRingsDisplayName());
    }

    private static void sendFeedback(String message) {
        Minecraft.getInstance().gui.getChat().addClientSystemMessage(Component.literal(message));
    }

    private static void resetSyncedSettings() {
        GlobeClientSettings.applySyncedFromServer(GlobeSettings.DEFAULT);
        GlobeMapTextureCache.reset();
    }
}
