package globe.world.client;

import globe.world.config.TilingSettings;
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
                    GlobeClientTilingSettings.applySyncedFromServer(payload.settings());
                    ClientConfigurationNetworking.send(GlobeWorldSettingsAckPayload.INSTANCE);
                }));

        ClientPlayNetworking.registerGlobalReceiver(GlobeWorldSettingsPayload.TYPE, (payload, context) ->
                GlobeClientTilingSettings.applySyncedFromServer(payload.settings()));

        ClientConfigurationConnectionEvents.DISCONNECT.register((listener, client) -> resetSyncedSettings());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> resetSyncedSettings());
    }

    private static void resetSyncedSettings() {
        GlobeClientTilingSettings.applySyncedFromServer(TilingSettings.DEFAULT);
    }
}
