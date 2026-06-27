package globe.world.map;

import globe.world.GlobeWorld;
import globe.world.network.GlobeMapSnapshotPayload;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GlobeMapTracker {
    private static final Identifier MASTERED_ATLAS_ADVANCEMENT = Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "mastered_atlas");
    private static final int UPDATE_INTERVAL_TICKS = 5;
    private static final int SYNC_INTERVAL_TICKS = 20;
    private static final int REVEAL_RADIUS_BLOCKS = 48;
    private static final int REVEAL_MOVE_THRESHOLD_BLOCKS = 8;
    private static final int PERIODIC_REVEAL_TICKS = 200;
    private static final int MAX_PIXELS_PER_PLAYER_REVEAL = 20_000;
    private static final Map<ResourceKey<Level>, Integer> LAST_SENT_REVISIONS = new HashMap<>();
    private static final Map<UUID, RevealState> LAST_REVEALS = new HashMap<>();

    private GlobeMapTracker() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GlobeMapTracker::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendInitialSnapshot(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_REVEALS.remove(handler.player.getUUID()));
    }

    public static void refreshChangedColumn(final ServerLevel level, final BlockPos pos) {
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return;
        }

        DimensionTiling tiling = DimensionTiling.forDimension(Level.OVERWORLD);
        if (!tiling.enabled()) {
            return;
        }

        GlobeMapSavedData data = GlobeMapSavedData.getIfPresent(level, tiling);
        if (data != null) {
            data.refreshColumn(level, tiling, pos);
        }
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
        revealForPlayers(server, overworld, tiling, data);
        awardCompletionAdvancement(server, overworld, data);

        if (server.getTickCount() % SYNC_INTERVAL_TICKS == 0) {
            sendIfChanged(overworld, data);
        }
    }

    private static void revealForPlayers(
            final MinecraftServer server,
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeMapSavedData data) {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (player.level() == level && !player.isSpectator()) {
                revealForPlayer(server, level, tiling, data, player);
            }
        }
    }

    private static void revealForPlayer(
            final MinecraftServer server,
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeMapSavedData data,
            final ServerPlayer player) {
        double canonicalX = CoordUtil.wrapBlock(tiling, player.getX());
        double canonicalZ = CoordUtil.wrapBlock(tiling, player.getZ());
        UUID playerId = player.getUUID();
        RevealState previous = LAST_REVEALS.get(playerId);
        if (previous != null && !previous.shouldReveal(tiling, canonicalX, canonicalZ, server.getTickCount())) {
            return;
        }

        boolean changed = data.revealAround(
                level,
                tiling,
                canonicalX,
                canonicalZ,
                REVEAL_RADIUS_BLOCKS,
                MAX_PIXELS_PER_PLAYER_REVEAL);
        if (!changed) {
            LAST_REVEALS.put(playerId, new RevealState(canonicalX, canonicalZ, server.getTickCount()));
        }
    }

    private static void awardCompletionAdvancement(
            final MinecraftServer server,
            final ServerLevel level,
            final GlobeMapSavedData data) {
        if (!data.complete()) {
            return;
        }

        AdvancementHolder advancement = server.getAdvancements().get(MASTERED_ATLAS_ADVANCEMENT);
        if (advancement == null) {
            return;
        }

        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (player.level() == level && !player.isSpectator()) {
                player.getAdvancements().award(advancement, "completed");
            }
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

    private record RevealState(double x, double z, int tick) {
        boolean shouldReveal(final DimensionTiling tiling, final double currentX, final double currentZ, final int currentTick) {
            if (currentTick - this.tick >= PERIODIC_REVEAL_TICKS) {
                return true;
            }

            double moveThresholdSqr = (double)REVEAL_MOVE_THRESHOLD_BLOCKS * REVEAL_MOVE_THRESHOLD_BLOCKS;
            return CoordUtil.wrappedDistanceSqrXZ(tiling, currentX, currentZ, this.x, this.z) >= moveThresholdSqr;
        }
    }
}
