package globe.world.util;

import globe.world.diagnostics.DiagnosticsChannel;
import globe.world.diagnostics.GlobeDiagnostics;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ChunkPacketUtil {
    private ChunkPacketUtil() {
    }

    public static List<Packet<?>> virtualizeBiomeResendForLoadedAliases(
            ClientboundChunksBiomesPacket packet,
            ServerPlayer viewer) {
        TopologyContext topology = TopologyContexts.forLevel(viewer.level());
        if (!topology.enabled() || packet.chunkBiomeData().isEmpty()) {
            return List.of(packet);
        }

        List<ClientboundChunksBiomesPacket.ChunkBiomeData> virtualData = new ArrayList<>();
        Set<Long> seenAliases = new HashSet<>();
        int fallbackCount = 0;

        for (ClientboundChunksBiomesPacket.ChunkBiomeData data : packet.chunkBiomeData()) {
            ChunkPos sourcePos = data.pos();
            ChunkPos canonicalChunk = topology.canonicalChunk(sourcePos);
            List<ChunkPos> aliases = topology.loadedAliasesFor(viewer, canonicalChunk);

            if (aliases.isEmpty()) {
                ChunkPos virtualChunk = topology.virtualChunkForViewer(canonicalChunk, viewer);
                fallbackCount++;
                addBiomeData(virtualData, seenAliases, data, virtualChunk);
                continue;
            }

            for (ChunkPos alias : aliases) {
                addBiomeData(virtualData, seenAliases, data, alias);
            }
        }

        logBiomeResend(viewer, packet.chunkBiomeData().size(), virtualData.size(), fallbackCount);
        if (virtualData.isEmpty()) {
            return List.of(packet);
        }
        return List.of(new ClientboundChunksBiomesPacket(virtualData));
    }

    private static void addBiomeData(
            List<ClientboundChunksBiomesPacket.ChunkBiomeData> virtualData,
            Set<Long> seenAliases,
            ClientboundChunksBiomesPacket.ChunkBiomeData sourceData,
            ChunkPos alias) {
        if (!seenAliases.add(alias.pack())) {
            return;
        }

        virtualData.add(new ClientboundChunksBiomesPacket.ChunkBiomeData(alias, sourceData.buffer().clone()));
    }

    private static void logBiomeResend(ServerPlayer viewer, int sourceCount, int aliasCount, int fallbackCount) {
        GlobeDiagnostics.debug(
                DiagnosticsChannel.PACKETS,
                "GW_BIOME_ALIAS_FANOUT player={} source_chunks={} alias_chunks={} fallbacks={}",
                viewer.getScoreboardName(),
                sourceCount,
                aliasCount,
                fallbackCount
        );
    }
}
