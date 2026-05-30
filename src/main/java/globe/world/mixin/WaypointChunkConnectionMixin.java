package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.waypoints.Waypoint;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

@Mixin(targets = "net.minecraft.world.waypoints.WaypointTransmitter$EntityChunkConnection")
public class WaypointChunkConnectionMixin {
    @Shadow
    @Final
    private ServerPlayer receiver;

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
        return original.call(identifier, icon, virtualChunk(chunk));
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
        return original.call(identifier, icon, virtualChunk(chunk));
    }

    private ChunkPos virtualChunk(ChunkPos chunk) {
        ChunkPos playerChunk = this.receiver.chunkPosition();
        return new ChunkPos(
                CoordUtil.virtualChunk(this.receiver.level(), CoordUtil.wrapChunk(this.receiver.level(), chunk.x()), playerChunk.x()),
                CoordUtil.virtualChunk(this.receiver.level(), CoordUtil.wrapChunk(this.receiver.level(), chunk.z()), playerChunk.z())
        );
    }
}
