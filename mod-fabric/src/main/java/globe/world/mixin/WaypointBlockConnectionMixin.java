package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.DimensionTiling;
import globe.world.util.WaypointPacketUtil;
import globe.world.util.WorldEventPacketUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.waypoints.Waypoint;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

@Mixin(targets = "net.minecraft.world.waypoints.WaypointTransmitter$EntityBlockConnection")
public class WaypointBlockConnectionMixin {
    @Shadow
    @Final
    private ServerPlayer receiver;

    @Shadow
    @Final
    private LivingEntity source;

    @WrapOperation(
            method = "connect",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundTrackedWaypointPacket;addWaypointPosition(Ljava/util/UUID;Lnet/minecraft/world/waypoints/Waypoint$Icon;Lnet/minecraft/core/Vec3i;)Lnet/minecraft/network/protocol/game/ClientboundTrackedWaypointPacket;"
            )
    )
    private ClientboundTrackedWaypointPacket virtualizeAddedWaypointPosition(
            UUID identifier,
            Waypoint.Icon icon,
            Vec3i position,
            Operation<ClientboundTrackedWaypointPacket> original) {
        return original.call(identifier, icon, virtualBlockPos(position));
    }

    @WrapOperation(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundTrackedWaypointPacket;updateWaypointPosition(Ljava/util/UUID;Lnet/minecraft/world/waypoints/Waypoint$Icon;Lnet/minecraft/core/Vec3i;)Lnet/minecraft/network/protocol/game/ClientboundTrackedWaypointPacket;"
            )
    )
    private ClientboundTrackedWaypointPacket virtualizeUpdatedWaypointPosition(
            UUID identifier,
            Waypoint.Icon icon,
            Vec3i position,
            Operation<ClientboundTrackedWaypointPacket> original) {
        return original.call(identifier, icon, virtualBlockPos(position));
    }

    private BlockPos virtualBlockPos(Vec3i position) {
        BlockPos pos = new BlockPos(position.getX(), position.getY(), position.getZ());
        return WorldEventPacketUtil.virtualizeBlockPos(this.receiver.level(), pos, this.receiver);
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
}
