package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.DimensionTiling;
import globe.world.util.WaypointPacketUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
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
        return WaypointPacketUtil.doesSourceIgnoreReceiver(source, receiver);
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
        return WaypointPacketUtil.isReallyFar(source, receiver);
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

        return WaypointPacketUtil.isChunkVisible(chunkPos, receiver);
    }
}
