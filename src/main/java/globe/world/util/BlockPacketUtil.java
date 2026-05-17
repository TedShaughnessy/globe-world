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
            (int) CoordUtil.virtualBlock(sectionPos.minBlockX(), viewer.getX())
        );
        int virtualSectionZ = SectionPos.blockToSectionCoord(
            (int) CoordUtil.virtualBlock(sectionPos.minBlockZ(), viewer.getZ())
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

    private static BlockPos virtualBlockPos(BlockPos pos, ServerPlayer viewer) {
        int x = (int) CoordUtil.virtualBlock(pos.getX(), viewer.getX());
        int z = (int) CoordUtil.virtualBlock(pos.getZ(), viewer.getZ());
        if (x == pos.getX() && z == pos.getZ()) return pos;
        return new BlockPos(x, pos.getY(), z);
    }
}
