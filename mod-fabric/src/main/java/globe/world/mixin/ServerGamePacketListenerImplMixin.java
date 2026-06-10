package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologyContexts;
import globe.world.util.ClientActionDiagnostics;
import globe.world.util.CoordUtil;
import globe.world.util.EntityCanonicalizer;
import globe.world.util.EntityPacketUtil;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
    @Shadow public ServerPlayer player;
    @Shadow private Entity lastVehicle;
    @Shadow private double vehicleFirstGoodX;
    @Shadow private double vehicleFirstGoodY;
    @Shadow private double vehicleFirstGoodZ;
    @Shadow private double vehicleLastGoodX;
    @Shadow private double vehicleLastGoodY;
    @Shadow private double vehicleLastGoodZ;

    @ModifyVariable(method = "handleMoveVehicle", at = @At("HEAD"), argsOnly = true)
    private ServerboundMoveVehiclePacket canonicalizeInboundVehicleMove(ServerboundMoveVehiclePacket packet) {
        Entity vehicle = this.player.getRootVehicle();
        if (vehicle == this.player || vehicle.getControllingPassenger() != this.player) {
            return packet;
        }

        Vec3 position = packet.position();
        double anchorX = vehicle == this.lastVehicle ? this.vehicleLastGoodX : vehicle.getX();
        double anchorZ = vehicle == this.lastVehicle ? this.vehicleLastGoodZ : vehicle.getZ();
        double x = CoordUtil.virtualBlock(
                this.player.level(),
                CoordUtil.wrapBlock(this.player.level(), position.x),
                anchorX);
        double z = CoordUtil.virtualBlock(
                this.player.level(),
                CoordUtil.wrapBlock(this.player.level(), position.z),
                anchorZ);
        if (x == position.x && z == position.z) {
            return packet;
        }
        return new ServerboundMoveVehiclePacket(
                new Vec3(x, position.y, z),
                packet.yRot(),
                packet.xRot(),
                packet.onGround());
    }

    @Inject(method = "handleMoveVehicle", at = @At("TAIL"))
    private void canonicalizeAcceptedVehicleMove(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        Entity vehicle = this.player.getRootVehicle();
        if (vehicle == this.player || vehicle.getControllingPassenger() != this.player || vehicle != this.lastVehicle) {
            return;
        }

        for (Entity passenger : vehicle.getPassengers()) {
            vehicle.positionRider(passenger);
        }

        if (!EntityCanonicalizer.canonicalizeRootStack(vehicle)) {
            this.player.level().getChunkSource().move(this.player);
            return;
        }

        this.vehicleFirstGoodX = vehicle.getX();
        this.vehicleFirstGoodY = vehicle.getY();
        this.vehicleFirstGoodZ = vehicle.getZ();
        this.vehicleLastGoodX = vehicle.getX();
        this.vehicleLastGoodY = vehicle.getY();
        this.vehicleLastGoodZ = vehicle.getZ();
        this.player.level().getChunkSource().move(this.player);
    }

    @WrapOperation(
        method = "handleMoveVehicle",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private void virtualizeVehicleCorrectionPacket(
            ServerGamePacketListenerImpl connection,
            Packet<?> packet,
            Operation<Void> original) {
        @SuppressWarnings("unchecked")
        Packet<? super ClientGamePacketListener> gamePacket = (Packet<? super ClientGamePacketListener>) packet;
        original.call(connection, EntityPacketUtil.virtualizeFor(gamePacket, connection.player));
    }

    @WrapOperation(
        method = "handleUseItemOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;mayInteract(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean rejectAliasUseItemOnOutsideCanonicalSimulation(
            ServerLevel level,
            Entity entity,
            BlockPos pos,
            Operation<Boolean> original) {
        boolean mayInteract = original.call(level, entity, pos);
        if (!mayInteract) {
            return false;
        }
        if (entity instanceof ServerPlayer player
                && ClientActionDiagnostics.shouldRejectAliasMutation(player, level, pos, "use_item_on")) {
            return false;
        }
        return true;
    }

    @Inject(method = "updateSignText", at = @At("HEAD"), cancellable = true)
    private void rejectAliasSignUpdateOutsideCanonicalSimulation(
            ServerboundSignUpdatePacket packet,
            List<?> lines,
            CallbackInfo ci) {
        ServerLevel level = this.player.level();
        if (ClientActionDiagnostics.shouldRejectAliasMutation(this.player, level, packet.getPos(), "sign_update")) {
            ci.cancel();
        }
    }

    @WrapOperation(
        method = "updateSignText",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ServerboundSignUpdatePacket;getPos()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos canonicalizeSignUpdatePos(
            ServerboundSignUpdatePacket packet,
            Operation<BlockPos> original) {
        return TopologyContexts.forLevel(this.player.level()).canonicalBlock(original.call(packet));
    }
}
