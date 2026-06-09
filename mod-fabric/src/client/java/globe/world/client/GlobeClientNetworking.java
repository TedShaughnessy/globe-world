package globe.world.client;

import globe.world.config.GlobeSettings;
import globe.world.network.GlobeWorldSettingsAckPayload;
import globe.world.network.GlobeWorldSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

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

        ClientConfigurationConnectionEvents.DISCONNECT.register((listener, client) -> resetSyncedSettings());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> resetSyncedSettings());
    }

    private static void resetSyncedSettings() {
        GlobeClientSettings.applySyncedFromServer(GlobeSettings.DEFAULT);
    }
}
