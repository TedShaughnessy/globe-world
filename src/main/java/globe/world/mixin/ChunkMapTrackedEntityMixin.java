package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import globe.world.util.EntityPacketUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class ChunkMapTrackedEntityMixin {

    @Shadow
    @Final
    private Entity entity;

    @WrapOperation(
        method = "updatePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private Vec3 wrapTrackingDelta(Vec3 playerPos, Vec3 entityPos, Operation<Vec3> original) {
        Vec3 delta = original.call(playerPos, entityPos);
        return new Vec3(
            CoordUtil.wrappedDeltaBlock(playerPos.x, entityPos.x),
            delta.y,
            CoordUtil.wrappedDeltaBlock(playerPos.z, entityPos.z)
        );
    }

    @WrapOperation(
        method = {
            "sendToTrackingPlayers",
            "sendToTrackingPlayersFiltered"
        },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerConnection;send(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private void virtualizeTrackedPacket(
            ServerPlayerConnection connection,
            Packet<? super ClientGamePacketListener> packet,
            Operation<Void> original) {
        original.call(connection, EntityPacketUtil.virtualizeFor(packet, connection.getPlayer()));
    }

    @WrapOperation(
        method = "sendToTrackingPlayersAndSelf",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private void virtualizeSelfPacket(
            ServerGamePacketListenerImpl connection,
            Packet<? super ClientGamePacketListener> packet,
            Operation<Void> original) {
        if (entity instanceof ServerPlayer player) {
            original.call(connection, EntityPacketUtil.virtualizeFor(packet, player));
            return;
        }
        original.call(connection, packet);
    }
}
