package globe.world.map;

import globe.world.network.GlobeMapSnapshotPayload;
import globe.world.util.DimensionTiling;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GlobeMapTracker {
    private static final int UPDATE_INTERVAL_TICKS = 1;
    private static final int SYNC_INTERVAL_TICKS = 20;
    private static final int FILL_PIXEL_BUDGET = 8192;
    private static final Map<ResourceKey<Level>, Integer> LAST_SENT_REVISIONS = new HashMap<>();

    private GlobeMapTracker() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GlobeMapTracker::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendInitialSnapshot(handler.player));
    }

    private static void tick(final MinecraftServer server) {
        if (server.getTickCount() % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        DimensionTiling tiling = DimensionTiling.forDimension(Level.OVERWORLD);
        if (!tiling.enabled()) {
            return;
        }

        GlobeMapSavedData data = GlobeMapSavedData.get(overworld, tiling);
        data.fillNextPixels(overworld, tiling, FILL_PIXEL_BUDGET);

        if (server.getTickCount() % SYNC_INTERVAL_TICKS == 0) {
            sendIfChanged(overworld, data);
        }
    }

    private static void sendInitialSnapshot(final ServerPlayer player) {
        ServerLevel level = player.level();
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return;
        }

        DimensionTiling tiling = DimensionTiling.forDimension(Level.OVERWORLD);
        if (tiling.enabled()) {
            send(player, GlobeMapSavedData.get(level, tiling));
        }
    }

    private static void sendIfChanged(final ServerLevel level, final GlobeMapSavedData data) {
        int lastSentRevision = LAST_SENT_REVISIONS.getOrDefault(level.dimension(), -1);
        if (lastSentRevision == data.revision()) {
            return;
        }

        GlobeMapSnapshotPayload payload = GlobeMapSnapshotPayload.from(data);
        for (ServerPlayer player : List.copyOf(level.getServer().getPlayerList().getPlayers())) {
            if (player.level() == level && ServerPlayNetworking.canSend(player, GlobeMapSnapshotPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
        LAST_SENT_REVISIONS.put(level.dimension(), data.revision());
    }

    private static void send(final ServerPlayer player, final GlobeMapSavedData data) {
        if (ServerPlayNetworking.canSend(player, GlobeMapSnapshotPayload.TYPE)) {
            ServerPlayNetworking.send(player, GlobeMapSnapshotPayload.from(data));
        }
    }
}
