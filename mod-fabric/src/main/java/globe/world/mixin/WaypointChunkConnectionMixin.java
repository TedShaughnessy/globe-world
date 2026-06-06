package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.DimensionTiling;
import globe.world.util.WaypointPacketUtil;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.waypoints.Waypoint;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(targets = "net.minecraft.world.waypoints.WaypointTransmitter$EntityChunkConnection")
public class WaypointChunkConnectionMixin {
    @Shadow
    @Final
    private ServerPlayer receiver;

    @Shadow
    @Final
    private LivingEntity source;

    @Shadow
    @Final
    private Waypoint.Icon icon;

    @Shadow
    private ChunkPos lastPosition;

    @Unique
    private ChunkPos globeWorld$lastVirtualChunk;

    @WrapOperation(
            method = "connect",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundTrackedWaypointPacket;addWaypointChunk(Ljava/util/UUID;Lnet/minecraft/world/waypoints/Waypoint$Icon;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/network/protocol/game/ClientboundTrackedWaypointPacket;"
            )
    )
    private ClientboundTrackedWaypointPacket virtualizeAddedWaypointChunk(
            UUID identifier,
            Waypoint.Icon icon,
            ChunkPos chunk,
            Operation<ClientboundTrackedWaypointPacket> original) {
        ChunkPos virtualChunk = virtualChunk(chunk);
        this.globeWorld$lastVirtualChunk = virtualChunk;
        return original.call(identifier, icon, virtualChunk);
    }

    @WrapOperation(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundTrackedWaypointPacket;updateWaypointChunk(Ljava/util/UUID;Lnet/minecraft/world/waypoints/Waypoint$Icon;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/network/protocol/game/ClientboundTrackedWaypointPacket;"
            )
    )
    private ClientboundTrackedWaypointPacket virtualizeUpdatedWaypointChunk(
            UUID identifier,
            Waypoint.Icon icon,
            ChunkPos chunk,
            Operation<ClientboundTrackedWaypointPacket> original) {
        ChunkPos virtualChunk = virtualChunk(chunk);
        this.globeWorld$lastVirtualChunk = virtualChunk;
        return original.call(identifier, icon, virtualChunk);
    }

    private ChunkPos virtualChunk(ChunkPos chunk) {
        return WaypointPacketUtil.virtualChunk(chunk, this.receiver);
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void updateWhenReceiverNearestAliasChanges(CallbackInfo ci) {
        if (!DimensionTiling.forLevel(this.source.level()).enabled()) {
            return;
        }

        ChunkPos virtualChunk = virtualChunk(this.lastPosition);
        if (virtualChunk.equals(this.globeWorld$lastVirtualChunk)) {
            return;
        }

        this.receiver.connection.send(ClientboundTrackedWaypointPacket.updateWaypointChunk(
                this.source.getUUID(),
                this.icon,
                virtualChunk
        ));
        this.globeWorld$lastVirtualChunk = virtualChunk;
    }

    @WrapOperation(
            method = "isBroken",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/waypoints/WaypointTransmitter;doesSourceIgnoreReceiver(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerPlayer;)Z"
            )
    )
    private boolean useWrappedIgnoreCheck(
            LivingEntity source,
            ServerPlayer receiver,
            Operation<Boolean> original) {
        if (!DimensionTiling.forLevel(source.level()).enabled()) {
            return original.call(source, receiver);
        }
        return WaypointPacketUtil.doesSourceIgnoreReceiver(source, receiver);
    }

    @WrapOperation(
            method = "isBroken",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/waypoints/WaypointTransmitter;isChunkVisible(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/server/level/ServerPlayer;)Z"
            )
    )
    private boolean useWrappedChunkVisibility(
            ChunkPos chunk,
            ServerPlayer receiver,
            Operation<Boolean> original) {
        if (!DimensionTiling.forLevel(this.source.level()).enabled()) {
            return original.call(chunk, receiver);
        }
        return WaypointPacketUtil.isChunkVisible(chunk, receiver);
    }
}
