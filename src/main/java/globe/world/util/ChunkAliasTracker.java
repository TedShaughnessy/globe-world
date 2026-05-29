package globe.world.util;

import globe.world.GlobeWorld;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ChunkAliasTracker {
    private static final ConcurrentMap<PlayerDimensionKey, ConcurrentMap<Long, ConcurrentMap<Long, Boolean>>> ALIASES =
            new ConcurrentHashMap<>();

    public static void addAlias(
            ServerPlayer player,
            ResourceKey<Level> dimension,
            int canonicalX,
            int canonicalZ,
            int aliasX,
            int aliasZ) {
        aliasesFor(player, dimension, canonicalX, canonicalZ).put(key(aliasX, aliasZ), Boolean.TRUE);
    }

    public static void removeAlias(
            ServerPlayer player,
            ResourceKey<Level> dimension,
            int canonicalX,
            int canonicalZ,
            int aliasX,
            int aliasZ) {
        PlayerDimensionKey playerDimension = new PlayerDimensionKey(player.getUUID(), dimension);
        ConcurrentMap<Long, ConcurrentMap<Long, Boolean>> byCanonical = ALIASES.get(playerDimension);
        if (byCanonical == null) return;

        long canonicalKey = key(canonicalX, canonicalZ);
        ConcurrentMap<Long, Boolean> aliases = byCanonical.get(canonicalKey);
        if (aliases == null) return;

        aliases.remove(key(aliasX, aliasZ));
        if (aliases.isEmpty()) {
            byCanonical.remove(canonicalKey);
        }
        if (byCanonical.isEmpty()) {
            ALIASES.remove(playerDimension);
        }
    }

    public static List<ChunkPos> aliasesForCanonical(
            ServerPlayer player,
            ResourceKey<Level> dimension,
            int canonicalX,
            int canonicalZ) {
        ConcurrentMap<Long, ConcurrentMap<Long, Boolean>> byCanonical =
                ALIASES.get(new PlayerDimensionKey(player.getUUID(), dimension));
        if (byCanonical == null) return List.of();

        ConcurrentMap<Long, Boolean> aliases = byCanonical.get(key(canonicalX, canonicalZ));
        if (aliases == null || aliases.isEmpty()) return List.of();

        List<ChunkPos> result = new ArrayList<>(aliases.size());
        for (long aliasKey : aliases.keySet()) {
            result.add(unpack(aliasKey));
        }
        return List.copyOf(result);
    }

    public static int clearPlayer(ServerPlayer player) {
        int removed = 0;
        UUID playerId = player.getUUID();
        for (PlayerDimensionKey playerDimension : ALIASES.keySet()) {
            if (playerDimension.playerId().equals(playerId)) {
                removed += removePlayerDimension(playerDimension);
            }
        }
        logCleanup("player", removed, player.getScoreboardName(), null);
        return removed;
    }

    public static int clearPlayerDimension(ServerPlayer player, ResourceKey<Level> dimension) {
        int removed = removePlayerDimension(new PlayerDimensionKey(player.getUUID(), dimension));
        logCleanup("player_dimension", removed, player.getScoreboardName(), dimension);
        return removed;
    }

    public static int clearLevel(ResourceKey<Level> dimension) {
        int removed = 0;
        for (PlayerDimensionKey playerDimension : ALIASES.keySet()) {
            if (playerDimension.dimension().equals(dimension)) {
                removed += removePlayerDimension(playerDimension);
            }
        }
        logCleanup("level", removed, null, dimension);
        return removed;
    }

    public static int clearAll() {
        int removed = totalAliasRecords();
        ALIASES.clear();
        logCleanup("all", removed, null, null);
        return removed;
    }

    public static int totalAliasRecords() {
        int total = 0;
        for (ConcurrentMap<Long, ConcurrentMap<Long, Boolean>> byCanonical : ALIASES.values()) {
            total += countAliases(byCanonical);
        }
        return total;
    }

    public static int aliasRecordsForPlayer(ServerPlayer player) {
        int total = 0;
        UUID playerId = player.getUUID();
        for (PlayerDimensionKey playerDimension : ALIASES.keySet()) {
            if (!playerDimension.playerId().equals(playerId)) {
                continue;
            }

            ConcurrentMap<Long, ConcurrentMap<Long, Boolean>> byCanonical = ALIASES.get(playerDimension);
            if (byCanonical != null) {
                total += countAliases(byCanonical);
            }
        }
        return total;
    }

    public static int aliasRecordsForPlayerDimension(ServerPlayer player, ResourceKey<Level> dimension) {
        ConcurrentMap<Long, ConcurrentMap<Long, Boolean>> byCanonical =
                ALIASES.get(new PlayerDimensionKey(player.getUUID(), dimension));
        return byCanonical == null ? 0 : countAliases(byCanonical);
    }

    private static ConcurrentMap<Long, Boolean> aliasesFor(
            ServerPlayer player,
            ResourceKey<Level> dimension,
            int canonicalX,
            int canonicalZ) {
        return ALIASES
                .computeIfAbsent(new PlayerDimensionKey(player.getUUID(), dimension), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(key(canonicalX, canonicalZ), ignored -> new ConcurrentHashMap<>());
    }

    private static int removePlayerDimension(PlayerDimensionKey playerDimension) {
        ConcurrentMap<Long, ConcurrentMap<Long, Boolean>> removed = ALIASES.remove(playerDimension);
        return removed == null ? 0 : countAliases(removed);
    }

    private static int countAliases(ConcurrentMap<Long, ConcurrentMap<Long, Boolean>> byCanonical) {
        int total = 0;
        for (ConcurrentMap<Long, Boolean> aliases : byCanonical.values()) {
            total += aliases.size();
        }
        return total;
    }

    private static void logCleanup(
            String scope,
            int removed,
            String playerName,
            ResourceKey<Level> dimension) {
        if (removed <= 0 || !GlobeWorld.LOGGER.isDebugEnabled()) {
            return;
        }

        GlobeWorld.LOGGER.debug(
                "GW_CHUNK_ALIAS_TRACKER cleanup scope={} removed={} player={} dimension={}",
                scope,
                removed,
                playerName == null ? "*" : playerName,
                dimension == null ? "*" : dimension.identifier()
        );
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | ((long) z & 0xFFFFFFFFL);
    }

    private static ChunkPos unpack(long key) {
        return new ChunkPos((int) (key >> 32), (int) key);
    }

    private record PlayerDimensionKey(UUID playerId, ResourceKey<Level> dimension) {
    }
}
