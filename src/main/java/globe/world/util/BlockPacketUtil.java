package globe.world.util;

import globe.world.mixin.ClientboundBlockEntityDataPacketAccessor;
import globe.world.mixin.ClientboundSectionBlocksUpdatePacketAccessor;
import it.unimi.dsi.fastutil.shorts.ShortArraySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

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

        int virtualSectionX = SectionPos.blockToSectionCoord(
            (int) CoordUtil.virtualBlock(viewer.level(), sectionPos.minBlockX(), viewer.getX())
        );
        int virtualSectionZ = SectionPos.blockToSectionCoord(
            (int) CoordUtil.virtualBlock(viewer.level(), sectionPos.minBlockZ(), viewer.getZ())
        );

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

    private static List<Packet<?>> virtualizeBlockUpdateForLoadedAliases(
            ClientboundBlockUpdatePacket packet,
            ServerPlayer viewer) {
        BlockPos canonicalPos = CoordUtil.wrapBlockPos(viewer.level(), packet.getPos());
        int canonicalChunkX = SectionPos.blockToSectionCoord(canonicalPos.getX());
        int canonicalChunkZ = SectionPos.blockToSectionCoord(canonicalPos.getZ());
        List<ChunkPos> aliases = ChunkAliasTracker.aliasesForCanonical(viewer, canonicalChunkX, canonicalChunkZ);
        if (aliases.isEmpty()) {
            return List.of(virtualizeBlockUpdate(packet, viewer));
        }

        List<Packet<?>> packets = new ArrayList<>(aliases.size());
        for (ChunkPos alias : aliases) {
            packets.add(new ClientboundBlockUpdatePacket(offsetBlockPos(canonicalPos, canonicalChunkX, canonicalChunkZ, alias),
                    packet.getBlockState()));
        }
        return packets;
    }

    private static List<Packet<?>> virtualizeBlockEntityUpdateForLoadedAliases(
            ClientboundBlockEntityDataPacket packet,
            ServerPlayer viewer) {
        BlockPos canonicalPos = CoordUtil.wrapBlockPos(viewer.level(), packet.getPos());
        int canonicalChunkX = SectionPos.blockToSectionCoord(canonicalPos.getX());
        int canonicalChunkZ = SectionPos.blockToSectionCoord(canonicalPos.getZ());
        List<ChunkPos> aliases = ChunkAliasTracker.aliasesForCanonical(viewer, canonicalChunkX, canonicalChunkZ);
        if (aliases.isEmpty()) {
            return List.of(virtualizeBlockEntityUpdate(packet, viewer));
        }

        List<Packet<?>> packets = new ArrayList<>(aliases.size());
        for (ChunkPos alias : aliases) {
            packets.add(ClientboundBlockEntityDataPacketAccessor.globeWorld$new(
                    offsetBlockPos(canonicalPos, canonicalChunkX, canonicalChunkZ, alias),
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
        int canonicalChunkX = CoordUtil.wrapChunk(viewer.level(), sectionPos.x());
        int canonicalChunkZ = CoordUtil.wrapChunk(viewer.level(), sectionPos.z());
        List<ChunkPos> aliases = ChunkAliasTracker.aliasesForCanonical(viewer, canonicalChunkX, canonicalChunkZ);
        if (aliases.isEmpty()) {
            return List.of(virtualizeSectionUpdate(packet, viewer));
        }

        List<Packet<?>> packets = new ArrayList<>(aliases.size());
        for (ChunkPos alias : aliases) {
            packets.add(copySectionUpdate(packet, SectionPos.of(alias.x(), sectionPos.y(), alias.z())));
        }
        return packets;
    }

    private static BlockPos virtualBlockPos(BlockPos pos, ServerPlayer viewer) {
        int x = (int) CoordUtil.virtualBlock(viewer.level(), pos.getX(), viewer.getX());
        int z = (int) CoordUtil.virtualBlock(viewer.level(), pos.getZ(), viewer.getZ());
        if (x == pos.getX() && z == pos.getZ()) return pos;
        return new BlockPos(x, pos.getY(), z);
    }

    private static BlockPos offsetBlockPos(
            BlockPos canonicalPos,
            int canonicalChunkX,
            int canonicalChunkZ,
            ChunkPos alias) {
        int dx = (alias.x() - canonicalChunkX) * 16;
        int dz = (alias.z() - canonicalChunkZ) * 16;
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
}
