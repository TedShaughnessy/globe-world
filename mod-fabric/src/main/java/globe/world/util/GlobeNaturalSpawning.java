package globe.world.util;

import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

public final class GlobeNaturalSpawning {
    private GlobeNaturalSpawning() {
    }

    public static boolean canSpawnEntitiesInChunkOrViewerAlias(
            ServerLevel level,
            ChunkPos pos,
            Predicate<ChunkPos> canSpawnEntitiesInChunk) {
        if (canSpawnEntitiesInChunk.test(pos)) {
            return true;
        }

        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled()) {
            return false;
        }

        ChunkPos canonicalPos = topology.canonicalChunk(pos);
        if (!canonicalPos.equals(pos) && canSpawnEntitiesInChunk.test(canonicalPos)) {
            return true;
        }

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.level() != level) {
                continue;
            }

            ChunkPos viewerAlias = topology.virtualChunkForViewer(canonicalPos, player);
            if (!viewerAlias.equals(pos) && canSpawnEntitiesInChunk.test(viewerAlias)) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasWrappedPlayerCloseForSpawning(ServerLevel level, ChunkPos pos) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled()) {
            return false;
        }

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.level() != level) {
                continue;
            }

            if (topology.wrappedChunkDistanceSqr(pos, player.position()) < 16384.0D) {
                return true;
            }
        }

        return false;
    }
}
