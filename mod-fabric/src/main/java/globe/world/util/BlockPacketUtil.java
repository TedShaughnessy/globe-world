package globe.world.util;

import globe.world.diagnostics.DiagnosticsChannel;
import globe.world.diagnostics.GlobeDiagnostics;
import globe.world.mixin.ClientboundBlockEntityDataPacketAccessor;
import globe.world.mixin.ClientboundLightUpdatePacketAccessor;
import globe.world.mixin.ClientboundSectionBlocksUpdatePacketAccessor;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.shorts.ShortArraySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockPacketUtil {
    public static Packet<?> virtualizeFor(Packet<?> packet, ServerPlayer viewer) {
        if (packet instanceof ClientboundBlockUpdatePacket blockUpdate) {
            return virtualizeBlockUpdate(blockUpdate, viewer);
        }
        if (packet instanceof ClientboundBlockEntityDataPacket blockEntityUpdate) {
            return virtualizeBlockEntityUpdate(blockEntityUpdate, viewer);
        }
        if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
            return virtualizeSectionUpdate(sectionUpdate, viewer);
        }
        if (packet instanceof ClientboundLightUpdatePacket lightUpdate) {
            return virtualizeLightUpdate(lightUpdate, viewer);
        }
        return packet;
    }

    public static List<Packet<?>> virtualizeForLoadedAliases(Packet<?> packet, ServerPlayer viewer) {
        if (packet instanceof ClientboundBlockUpdatePacket blockUpdate) {
            return virtualizeBlockUpdateForLoadedAliases(blockUpdate, viewer);
        }
        if (packet instanceof ClientboundBlockEntityDataPacket blockEntityUpdate) {
            return virtualizeBlockEntityUpdateForLoadedAliases(blockEntityUpdate, viewer);
        }
        if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
            return virtualizeSectionUpdateForLoadedAliases(sectionUpdate, viewer);
        }
        if (packet instanceof ClientboundLightUpdatePacket lightUpdate) {
            return virtualizeLightUpdateForLoadedAliases(lightUpdate, viewer);
        }
        return List.of(packet);
    }

    private static ClientboundBlockUpdatePacket virtualizeBlockUpdate(
            ClientboundBlockUpdatePacket packet,
            ServerPlayer viewer) {
        BlockPos pos = packet.getPos();
        BlockPos virtualPos = virtualBlockPos(pos, viewer);
        if (virtualPos.equals(pos)) return packet;
        return new ClientboundBlockUpdatePacket(virtualPos, packet.getBlockState());
    }

    private static ClientboundBlockEntityDataPacket virtualizeBlockEntityUpdate(
            ClientboundBlockEntityDataPacket packet,
            ServerPlayer viewer) {
        BlockPos pos = packet.getPos();
        BlockPos virtualPos = virtualBlockPos(pos, viewer);
        if (virtualPos.equals(pos)) return packet;
        return ClientboundBlockEntityDataPacketAccessor.globeWorld$new(
            virtualPos,
            packet.getType(),
            packet.getTag()
        );
    }

    private static ClientboundSectionBlocksUpdatePacket virtualizeSectionUpdate(
            ClientboundSectionBlocksUpdatePacket packet,
            ServerPlayer viewer) {
        ClientboundSectionBlocksUpdatePacketAccessor access =
            (ClientboundSectionBlocksUpdatePacketAccessor) packet;
        SectionPos sectionPos = access.globeWorld$getSectionPos();
        TopologyContext topology = TopologyContexts.forLevel(viewer.level());
        ChunkPos canonicalChunk = topology.canonicalChunk(sectionPos);
        ChunkPos virtualChunk = topology.virtualChunkForViewer(canonicalChunk, viewer);

        int virtualSectionX = virtualChunk.x();
        int virtualSectionZ = virtualChunk.z();

        if (virtualSectionX == sectionPos.x() && virtualSectionZ == sectionPos.z()) return packet;

        SectionPos virtualSection = SectionPos.of(virtualSectionX, sectionPos.y(), virtualSectionZ);
        short[] positions = access.globeWorld$getPositions();
        ClientboundSectionBlocksUpdatePacket copy =
            new ClientboundSectionBlocksUpdatePacket(virtualSection, new ShortArraySet(new short[0]), null);
        ClientboundSectionBlocksUpdatePacketAccessor copyAccess =
            (ClientboundSectionBlocksUpdatePacketAccessor) copy;
        copyAccess.globeWorld$setPositions(positions.clone());
        copyAccess.globeWorld$setStates(access.globeWorld$getStates().clone());
        return copy;
    }

    private static ClientboundLightUpdatePacket virtualizeLightUpdate(
            ClientboundLightUpdatePacket packet,
            ServerPlayer viewer) {
        ChunkPos virtualChunk = virtualLightChunk(packet, viewer);
        if (virtualChunk.x() == packet.getX() && virtualChunk.z() == packet.getZ()) return packet;
        return copyLightUpdate(packet, virtualChunk);
    }

    private static List<Packet<?>> virtualizeBlockUpdateForLoadedAliases(
            ClientboundBlockUpdatePacket packet,
            ServerPlayer viewer) {
        TopologyContext topology = TopologyContexts.forLevel(viewer.level());
        BlockPos canonicalPos = topology.canonicalBlock(packet.getPos());
        ChunkPos canonicalChunk = topology.canonicalChunkForBlock(canonicalPos);
        List<ChunkPos> aliases = topology.loadedAliasesFor(viewer, canonicalChunk);
        if (aliases.isEmpty()) {
            return List.of(virtualizeBlockUpdate(packet, viewer));
        }

        List<Packet<?>> packets = new ArrayList<>(aliases.size());
        for (ChunkPos alias : aliases) {
            packets.add(new ClientboundBlockUpdatePacket(offsetBlockPos(canonicalPos, canonicalChunk, alias),
                    packet.getBlockState()));
        }
        return packets;
    }

    private static List<Packet<?>> virtualizeBlockEntityUpdateForLoadedAliases(
            ClientboundBlockEntityDataPacket packet,
            ServerPlayer viewer) {
        TopologyContext topology = TopologyContexts.forLevel(viewer.level());
        BlockPos canonicalPos = topology.canonicalBlock(packet.getPos());
        ChunkPos canonicalChunk = topology.canonicalChunkForBlock(canonicalPos);
        List<ChunkPos> aliases = topology.loadedAliasesFor(viewer, canonicalChunk);
        if (aliases.isEmpty()) {
            return List.of(virtualizeBlockEntityUpdate(packet, viewer));
        }

        List<Packet<?>> packets = new ArrayList<>(aliases.size());
        for (ChunkPos alias : aliases) {
            packets.add(ClientboundBlockEntityDataPacketAccessor.globeWorld$new(
                    offsetBlockPos(canonicalPos, canonicalChunk, alias),
                    packet.getType(),
                    packet.getTag()
            ));
        }
        return packets;
    }

    private static List<Packet<?>> virtualizeSectionUpdateForLoadedAliases(
            ClientboundSectionBlocksUpdatePacket packet,
            ServerPlayer viewer) {
        ClientboundSectionBlocksUpdatePacketAccessor access =
            (ClientboundSectionBlocksUpdatePacketAccessor) packet;
        SectionPos sectionPos = access.globeWorld$getSectionPos();
        TopologyContext topology = TopologyContexts.forLevel(viewer.level());
        ChunkPos canonicalChunk = topology.canonicalChunk(sectionPos);
        List<ChunkPos> aliases = topology.loadedAliasesFor(viewer, canonicalChunk);
        if (aliases.isEmpty()) {
            return List.of(virtualizeSectionUpdate(packet, viewer));
        }

        List<Packet<?>> packets = new ArrayList<>(aliases.size());
        for (ChunkPos alias : aliases) {
            packets.add(copySectionUpdate(packet, SectionPos.of(alias.x(), sectionPos.y(), alias.z())));
        }
        return packets;
    }

    private static List<Packet<?>> virtualizeLightUpdateForLoadedAliases(
            ClientboundLightUpdatePacket packet,
            ServerPlayer viewer) {
        TopologyContext topology = TopologyContexts.forLevel(viewer.level());
        ChunkPos canonicalChunk = topology.canonicalChunk(packet.getX(), packet.getZ());
        List<ChunkPos> aliases = topology.loadedAliasesFor(viewer, canonicalChunk);
        if (aliases.isEmpty()) {
            ChunkPos virtualChunk = virtualLightChunk(packet, viewer, canonicalChunk);
            logLightFallback(packet, viewer, canonicalChunk, virtualChunk);
            if (virtualChunk.x() == packet.getX() && virtualChunk.z() == packet.getZ()) {
                return List.of(packet);
            }
            return List.of(copyLightUpdate(packet, virtualChunk));
        }

        Set<Long> seenAliases = new HashSet<>(aliases.size());
        List<Packet<?>> packets = new ArrayList<>(aliases.size());
        for (ChunkPos alias : aliases) {
            if (seenAliases.add(alias.pack())) {
                packets.add(copyLightUpdate(packet, alias));
            }
        }
        return packets;
    }

    private static BlockPos virtualBlockPos(BlockPos pos, ServerPlayer viewer) {
        TopologyContext topology = TopologyContexts.forLevel(viewer.level());
        return topology.virtualBlockForViewer(topology.canonicalBlock(pos), viewer);
    }

    private static BlockPos offsetBlockPos(
            BlockPos canonicalPos,
            ChunkPos canonicalChunk,
            ChunkPos alias) {
        int dx = (alias.x() - canonicalChunk.x()) * 16;
        int dz = (alias.z() - canonicalChunk.z()) * 16;
        if (dx == 0 && dz == 0) return canonicalPos;
        return canonicalPos.offset(dx, 0, dz);
    }

    private static ClientboundSectionBlocksUpdatePacket copySectionUpdate(
            ClientboundSectionBlocksUpdatePacket packet,
            SectionPos sectionPos) {
        ClientboundSectionBlocksUpdatePacketAccessor access =
            (ClientboundSectionBlocksUpdatePacketAccessor) packet;
        ClientboundSectionBlocksUpdatePacket copy =
            new ClientboundSectionBlocksUpdatePacket(sectionPos, new ShortArraySet(new short[0]), null);
        ClientboundSectionBlocksUpdatePacketAccessor copyAccess =
            (ClientboundSectionBlocksUpdatePacketAccessor) copy;
        copyAccess.globeWorld$setPositions(access.globeWorld$getPositions().clone());
        copyAccess.globeWorld$setStates(access.globeWorld$getStates().clone());
        return copy;
    }

    private static ChunkPos virtualLightChunk(ClientboundLightUpdatePacket packet, ServerPlayer viewer) {
        TopologyContext topology = TopologyContexts.forLevel(viewer.level());
        return virtualLightChunk(packet, viewer, topology.canonicalChunk(packet.getX(), packet.getZ()));
    }

    private static ChunkPos virtualLightChunk(
            ClientboundLightUpdatePacket packet,
            ServerPlayer viewer,
            ChunkPos canonicalChunk) {
        TopologyContext topology = TopologyContexts.forLevel(viewer.level());
        ChunkPos virtualChunk = topology.virtualChunkForViewer(canonicalChunk, viewer);
        int virtualX = virtualChunk.x();
        int virtualZ = virtualChunk.z();
        if (virtualX == packet.getX() && virtualZ == packet.getZ()) {
            return new ChunkPos(packet.getX(), packet.getZ());
        }
        return new ChunkPos(virtualX, virtualZ);
    }

    private static ClientboundLightUpdatePacket copyLightUpdate(
            ClientboundLightUpdatePacket packet,
            ChunkPos visibleChunk) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(visibleChunk.x());
            buffer.writeVarInt(visibleChunk.z());
            packet.getLightData().write(buffer);
            return ClientboundLightUpdatePacketAccessor.globeWorld$new(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void logLightFallback(
            ClientboundLightUpdatePacket packet,
            ServerPlayer viewer,
            ChunkPos canonicalChunk,
            ChunkPos virtualChunk) {
        if (!DimensionTiling.forLevel(viewer.level()).enabled()
                || (virtualChunk.x() == packet.getX() && virtualChunk.z() == packet.getZ())) {
            return;
        }

        GlobeDiagnostics.debug(
                DiagnosticsChannel.PACKETS,
                "GW_LIGHT_ALIAS_FANOUT fallback player={} original={} canonical={} virtual={}",
                viewer.getScoreboardName(),
                new ChunkPos(packet.getX(), packet.getZ()),
                canonicalChunk,
                virtualChunk
        );
    }
}
