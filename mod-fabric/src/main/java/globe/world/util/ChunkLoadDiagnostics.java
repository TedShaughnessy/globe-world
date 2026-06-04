package globe.world.util;

import globe.world.GlobeWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChunkLoadDiagnostics {
    private static final ConcurrentHashMap<BlockedAliasKey, AtomicInteger> BLOCKED_ALIAS_SENDS = new ConcurrentHashMap<>();
    private static final AtomicInteger TOTAL_BLOCKED_ALIAS_SENDS = new AtomicInteger();
    private static final AtomicInteger ACTIVE_BLOCKED_ALIASES = new AtomicInteger();
    private static final AtomicInteger TICKET_ACQUIRES = new AtomicInteger();
    private static final AtomicInteger TICKET_RELEASES = new AtomicInteger();
    private static final ConcurrentHashMap<PlayerSenderKey, Long> LAST_SENDER_LOG_TICK = new ConcurrentHashMap<>();

    private ChunkLoadDiagnostics() {
    }

    public static void blockedAliasSend(ServerPlayer player, ServerLevel level, ChunkPos aliasPos, ChunkPos canonicalPos) {
        BlockedAliasKey key = new BlockedAliasKey(player.getUUID(), dimensionName(level), aliasPos.pack(), canonicalPos.pack());
        AtomicInteger counter = BLOCKED_ALIAS_SENDS.computeIfAbsent(key, ignored -> {
            ACTIVE_BLOCKED_ALIASES.incrementAndGet();
            return new AtomicInteger();
        });
        int attempts = counter.incrementAndGet();
        int total = TOTAL_BLOCKED_ALIAS_SENDS.incrementAndGet();

        if (shouldLogBlockedAttempt(attempts)) {
            GlobeWorld.LOGGER.warn(
                    "GW_CHUNK_ALIAS_SEND blocked attempts={} totalBlocked={} activeBlocked={} player={} dimension={} alias={} canonical={}",
                    attempts,
                    total,
                    ACTIVE_BLOCKED_ALIASES.get(),
                    player.getScoreboardName(),
                    dimensionName(level),
                    format(aliasPos),
                    format(canonicalPos)
            );
        }
    }

    public static void recoveredAliasSend(ServerPlayer player, ServerLevel level, ChunkPos aliasPos, ChunkPos canonicalPos) {
        BlockedAliasKey key = new BlockedAliasKey(player.getUUID(), dimensionName(level), aliasPos.pack(), canonicalPos.pack());
        AtomicInteger counter = BLOCKED_ALIAS_SENDS.remove(key);
        if (counter == null) {
            return;
        }

        int active = ACTIVE_BLOCKED_ALIASES.decrementAndGet();
        GlobeWorld.LOGGER.warn(
                "GW_CHUNK_ALIAS_SEND recovered attempts={} activeBlocked={} player={} dimension={} alias={} canonical={}",
                counter.get(),
                active,
                player.getScoreboardName(),
                dimensionName(level),
                format(aliasPos),
                format(canonicalPos)
        );
    }

    public static void canonicalTicketAcquired(ServerLevel level, ChunkPos canonicalPos, int radius, int refs) {
        int count = TICKET_ACQUIRES.incrementAndGet();
        if (GlobeWorld.LOGGER.isDebugEnabled()) {
            GlobeWorld.LOGGER.debug(
                    "GW_CANONICAL_TICKET acquire count={} dimension={} canonical={} radius={} refs={}",
                    count,
                    dimensionName(level),
                    format(canonicalPos),
                    radius,
                    refs
            );
        }
    }

    public static void canonicalTicketReleased(ServerLevel level, ChunkPos canonicalPos, int radius) {
        int count = TICKET_RELEASES.incrementAndGet();
        if (GlobeWorld.LOGGER.isDebugEnabled()) {
            GlobeWorld.LOGGER.debug(
                    "GW_CANONICAL_TICKET release count={} dimension={} canonical={} radius={}",
                    count,
                    dimensionName(level),
                    format(canonicalPos),
                    radius
            );
        }
    }

    public static void senderState(
            ServerPlayer player,
            int pendingChunks,
            int unacknowledgedBatches,
            int maxUnacknowledgedBatches,
            float desiredChunksPerTick,
            float batchQuota) {
        if (pendingChunks <= 0 && unacknowledgedBatches <= 0) {
            return;
        }

        ServerLevel level = player.level();
        long gameTime = level.getGameTime();
        PlayerSenderKey key = new PlayerSenderKey(player.getUUID(), dimensionName(level));
        Long last = LAST_SENDER_LOG_TICK.get(key);
        if (last != null && gameTime - last < 20) {
            return;
        }
        LAST_SENDER_LOG_TICK.put(key, gameTime);

        GlobeWorld.LOGGER.warn(
                "GW_CHUNK_SENDER player={} dimension={} playerChunk={} pending={} unacked={}/{} desiredPerTick={} quota={}",
                player.getScoreboardName(),
                dimensionName(level),
                format(player.chunkPosition()),
                pendingChunks,
                unacknowledgedBatches,
                maxUnacknowledgedBatches,
                desiredChunksPerTick,
                batchQuota
        );
    }

    private static boolean shouldLogBlockedAttempt(int attempts) {
        return attempts <= 4 || (attempts & attempts - 1) == 0 || attempts % 100 == 0;
    }

    private static String format(ChunkPos pos) {
        return pos.x() + "," + pos.z();
    }

    private static String dimensionName(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    private record BlockedAliasKey(UUID playerId, String dimension, long aliasChunk, long canonicalChunk) {
    }

    private record PlayerSenderKey(UUID playerId, String dimension) {
    }
}
