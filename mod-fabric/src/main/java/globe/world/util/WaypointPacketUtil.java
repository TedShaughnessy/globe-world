package globe.world.util;

import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class WaypointPacketUtil {
    private WaypointPacketUtil() {
    }

    public static boolean doesSourceIgnoreReceiver(LivingEntity source, ServerPlayer receiver) {
        if (receiver.isSpectator()) {
            return false;
        }
        if (source.isSpectator() || source.hasIndirectPassenger(receiver)) {
            return true;
        }

        double broadcastRange = Math.min(
                source.getAttributeValue(Attributes.WAYPOINT_TRANSMIT_RANGE),
                receiver.getAttributeValue(Attributes.WAYPOINT_RECEIVE_RANGE)
        );
        TopologyContext topology = topology(source);
        return topology.wrappedDistanceSqr(source.position(), receiver.position())
                >= broadcastRange * broadcastRange;
    }

    public static boolean isReallyFar(LivingEntity source, ServerPlayer receiver) {
        TopologyContext topology = topology(source);
        return topology.wrappedDistanceSqr(source.position(), receiver.position()) > 332.0 * 332.0;
    }

    public static boolean isChunkVisible(ChunkPos chunkPos, ServerPlayer receiver) {
        ChunkPos virtualChunk = virtualChunk(chunkPos, receiver);
        return receiver.getChunkTrackingView().isInViewDistance(virtualChunk.x(), virtualChunk.z());
    }

    public static ChunkPos virtualChunk(ChunkPos chunkPos, ServerPlayer receiver) {
        TopologyContext topology = TopologyContexts.forLevel(receiver.level());
        return topology.virtualChunkForViewer(topology.canonicalChunk(chunkPos), receiver);
    }

    public static BlockPos virtualBlockPos(Vec3i position, ServerPlayer receiver) {
        TopologyContext topology = TopologyContexts.forLevel(receiver.level());
        BlockPos pos = new BlockPos(position.getX(), position.getY(), position.getZ());
        BlockPos canonical = topology.canonicalBlock(pos);
        return topology.virtualBlockForViewer(canonical, receiver);
    }

    public static float azimuthAngle(LivingEntity source, ServerPlayer receiver) {
        TopologyContext topology = TopologyContexts.forLevel(receiver.level());
        Vec3 sourcePos = topology.virtualBlockForViewer(
                topology.canonicalBlock(source.position()),
                receiver.position()
        );
        Vec3 direction = receiver.position().subtract(sourcePos).rotateClockwise90();
        return (float) Mth.atan2(direction.z(), direction.x());
    }

    private static TopologyContext topology(LivingEntity source) {
        return TopologyContexts.forLevel(source.level());
    }
}
