package globe.world.util;

import globe.world.diagnostics.DiagnosticsChannel;
import globe.world.diagnostics.GlobeDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalDiagnostics {
    private static final ConcurrentHashMap<PortalLogKey, Long> LAST_CONTACT_LOG = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<PortalLogKey, Long> LAST_PROCESS_LOG = new ConcurrentHashMap<>();

    private PortalDiagnostics() {
    }

    public static void portalContact(ServerLevel level, Entity entity, BlockPos portalPos, BlockState state) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        PortalLogKey key = key(player, "contact");
        long gameTime = level.getGameTime();
        if (!shouldLog(LAST_CONTACT_LOG, key, gameTime, 20)) {
            return;
        }

        GlobeDiagnostics.warn(
                DiagnosticsChannel.PORTALS,
                "GW_PORTAL_CONTACT player={} dimension={} playerPos={} playerChunk={} portalPos={} portalChunk={} block={} changingDimension={} cooldown={}",
                player.getScoreboardName(),
                dimensionName(level),
                format(player.blockPosition()),
                format(player.chunkPosition()),
                format(portalPos),
                format(new ChunkPos(SectionPos.blockToSectionCoord(portalPos.getX()), SectionPos.blockToSectionCoord(portalPos.getZ()))),
                state,
                player.isChangingDimension(),
                player.getPortalCooldown()
        );
    }

    public static void portalProcess(
            ServerLevel level,
            Entity entity,
            BlockPos entryPos,
            int portalTime,
            boolean insidePortalThisTick,
            boolean allowedToTeleport,
            boolean readyToTeleport) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        long gameTime = level.getGameTime();
        PortalLogKey key = key(player, "process");
        if (!readyToTeleport && allowedToTeleport && shouldSkipProcessLog(key, gameTime)) {
            return;
        }

        LAST_PROCESS_LOG.put(key, gameTime);
        GlobeDiagnostics.warn(
                DiagnosticsChannel.PORTALS,
                "GW_PORTAL_PROCESS player={} dimension={} playerPos={} entryPos={} portalTime={} insideThisTick={} allowed={} ready={} changingDimension={} cooldown={}",
                player.getScoreboardName(),
                dimensionName(level),
                format(player.blockPosition()),
                format(entryPos),
                portalTime,
                insidePortalThisTick,
                allowedToTeleport,
                readyToTeleport,
                player.isChangingDimension(),
                player.getPortalCooldown()
        );
    }

    public static void portalDestination(ServerLevel currentLevel, Entity entity, BlockPos entryPos, TeleportTransition transition) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        GlobeDiagnostics.warn(
                DiagnosticsChannel.PORTALS,
                "GW_PORTAL_DESTINATION player={} fromDimension={} entryPos={} result={} targetDimension={} targetPos={}",
                player.getScoreboardName(),
                dimensionName(currentLevel),
                format(entryPos),
                transition == null ? "null" : "ok",
                transition == null ? "null" : dimensionName(transition.newLevel()),
                transition == null ? "null" : transition.position()
        );
    }

    private static boolean shouldSkipProcessLog(PortalLogKey key, long gameTime) {
        Long last = LAST_PROCESS_LOG.get(key);
        return last != null && gameTime - last < 20;
    }

    private static boolean shouldLog(ConcurrentHashMap<PortalLogKey, Long> logs, PortalLogKey key, long gameTime, int intervalTicks) {
        Long last = logs.get(key);
        if (last != null && gameTime - last < intervalTicks) {
            return false;
        }
        logs.put(key, gameTime);
        return true;
    }

    private static PortalLogKey key(ServerPlayer player, String event) {
        return new PortalLogKey(player.getUUID(), event);
    }

    private static String dimensionName(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    private static String format(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String format(ChunkPos pos) {
        return pos.x() + "," + pos.z();
    }

    private record PortalLogKey(UUID playerId, String event) {
    }
}
