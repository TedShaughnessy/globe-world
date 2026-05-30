package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityWaypointMixin {
    @WrapOperation(
            method = "makeWaypointConnectionWith",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/waypoints/WaypointTransmitter;doesSourceIgnoreReceiver(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerPlayer;)Z"
            )
    )
    private boolean useWrappedWaypointTransmitRange(
            LivingEntity source,
            ServerPlayer receiver,
            Operation<Boolean> original) {
        if (!DimensionTiling.forLevel(source.level()).enabled()) {
            return original.call(source, receiver);
        }
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

    @WrapOperation(
            method = "makeWaypointConnectionWith",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/waypoints/WaypointTransmitter;isReallyFar(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerPlayer;)Z"
            )
    )
    private boolean useWrappedReallyFarWaypointDistance(
            LivingEntity source,
            ServerPlayer receiver,
            Operation<Boolean> original) {
        if (!DimensionTiling.forLevel(source.level()).enabled()) {
            return original.call(source, receiver);
        }
        return CoordUtil.wrappedDistanceSqr(source, receiver) > 332.0 * 332.0;
    }

    @WrapOperation(
            method = "makeWaypointConnectionWith",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/waypoints/WaypointTransmitter;isChunkVisible(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/server/level/ServerPlayer;)Z"
            )
    )
    private boolean useNearestWaypointChunkVisibility(
            ChunkPos chunkPos,
            ServerPlayer receiver,
            Operation<Boolean> original) {
        if (!DimensionTiling.forLevel(receiver.level()).enabled()) {
            return original.call(chunkPos, receiver);
        }

        ChunkPos playerChunk = receiver.chunkPosition();
        int virtualX = CoordUtil.virtualChunk(receiver.level(), CoordUtil.wrapChunk(receiver.level(), chunkPos.x()), playerChunk.x());
        int virtualZ = CoordUtil.virtualChunk(receiver.level(), CoordUtil.wrapChunk(receiver.level(), chunkPos.z()), playerChunk.z());
        return receiver.getChunkTrackingView().isInViewDistance(virtualX, virtualZ);
    }
}
