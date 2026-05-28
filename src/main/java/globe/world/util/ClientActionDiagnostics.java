package globe.world.util;

import globe.world.GlobeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientActionDiagnostics {
    private static final long LOG_INTERVAL_TICKS = 20L;
    private static final ConcurrentHashMap<RejectedActionKey, Long> LAST_REJECT_LOG_TICK = new ConcurrentHashMap<>();

    private ClientActionDiagnostics() {
    }

    public static boolean shouldRejectAliasMutation(ServerPlayer player, ServerLevel level, BlockPos rawPos, String action) {
        BlockPos canonicalPos = CoordUtil.wrapBlockPos(level, rawPos);
        if (canonicalPos.equals(rawPos)) {
            return false;
        }

        ChunkPos canonicalChunk = new ChunkPos(
                CoordUtil.wrapChunk(level, ChunkPos.containing(rawPos).x()),
                CoordUtil.wrapChunk(level, ChunkPos.containing(rawPos).z())
        );
        if (level.shouldTickBlocksAt(canonicalChunk.pack())) {
            return false;
        }

        logRejectedAliasMutation(player, level, rawPos, canonicalPos, canonicalChunk, action);
        return true;
    }

    private static void logRejectedAliasMutation(
            ServerPlayer player,
            ServerLevel level,
            BlockPos rawPos,
            BlockPos canonicalPos,
            ChunkPos canonicalChunk,
            String action) {
        ChunkPos rawChunk = ChunkPos.containing(rawPos);
        long gameTime = level.getGameTime();
        RejectedActionKey key = new RejectedActionKey(
                player.getUUID(),
                level.dimension().identifier().toString(),
                rawChunk.pack(),
                canonicalChunk.pack(),
                action
        );
        Long lastTick = LAST_REJECT_LOG_TICK.put(key, gameTime);
        if (lastTick != null && gameTime - lastTick < LOG_INTERVAL_TICKS) {
            return;
        }

        boolean rawBlockTicking = level.shouldTickBlocksAt(rawChunk.pack());
        boolean canonicalBlockTicking = level.shouldTickBlocksAt(canonicalChunk.pack());
        boolean canonicalLoaded = level.getChunkSource().getChunkNow(canonicalChunk.x(), canonicalChunk.z()) != null;

        GlobeWorld.LOGGER.warn(
                "GW_CLIENT_BLOCK_ACTION_REJECTED action={} reason=canonical_chunk_not_block_ticking player={} dimension={} rawPos={} rawChunk={} canonicalPos={} canonicalChunk={} playerChunk={} rawBlockTicking={} canonicalBlockTicking={} canonicalLoaded={}",
                action,
                player.getScoreboardName(),
                level.dimension().identifier(),
                rawPos,
                format(rawChunk),
                canonicalPos,
                format(canonicalChunk),
                format(player.chunkPosition()),
                rawBlockTicking,
                canonicalBlockTicking,
                canonicalLoaded
        );
    }

    private static String format(ChunkPos pos) {
        return pos.x() + "," + pos.z();
    }

    private record RejectedActionKey(UUID playerId, String dimension, long rawChunk, long canonicalChunk, String action) {
    }
}
