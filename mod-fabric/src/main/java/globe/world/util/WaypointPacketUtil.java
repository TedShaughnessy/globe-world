package globe.world.util;

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
        return CoordUtil.wrappedDistanceSqr(source, receiver) >= broadcastRange * broadcastRange;
    }

    public static boolean isReallyFar(LivingEntity source, ServerPlayer receiver) {
        return CoordUtil.wrappedDistanceSqr(source, receiver) > 332.0 * 332.0;
    }

    public static boolean isChunkVisible(ChunkPos chunkPos, ServerPlayer receiver) {
        ChunkPos playerChunk = receiver.chunkPosition();
        int virtualX = CoordUtil.virtualChunk(receiver.level(), CoordUtil.wrapChunk(receiver.level(), chunkPos.x()), playerChunk.x());
        int virtualZ = CoordUtil.virtualChunk(receiver.level(), CoordUtil.wrapChunk(receiver.level(), chunkPos.z()), playerChunk.z());
        return receiver.getChunkTrackingView().isInViewDistance(virtualX, virtualZ);
    }

    public static ChunkPos virtualChunk(ChunkPos chunkPos, ServerPlayer receiver) {
        ChunkPos playerChunk = receiver.chunkPosition();
        return new ChunkPos(
                CoordUtil.virtualChunk(receiver.level(), CoordUtil.wrapChunk(receiver.level(), chunkPos.x()), playerChunk.x()),
                CoordUtil.virtualChunk(receiver.level(), CoordUtil.wrapChunk(receiver.level(), chunkPos.z()), playerChunk.z())
        );
    }

    public static float azimuthAngle(LivingEntity source, ServerPlayer receiver) {
        double x = CoordUtil.virtualBlock(receiver.level(), CoordUtil.wrapBlock(receiver.level(), source.getX()), receiver.getX());
        double z = CoordUtil.virtualBlock(receiver.level(), CoordUtil.wrapBlock(receiver.level(), source.getZ()), receiver.getZ());
        Vec3 sourcePos = new Vec3(x, source.getY(), z);
        Vec3 direction = receiver.position().subtract(sourcePos).rotateClockwise90();
        return (float) Mth.atan2(direction.z(), direction.x());
    }
}
