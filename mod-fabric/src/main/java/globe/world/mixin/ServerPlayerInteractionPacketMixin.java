package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.BlockPacketUtil;
import globe.world.util.WorldEventPacketUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public class ServerPlayerInteractionPacketMixin {
    @WrapOperation(
            method = {
                    "lookAt(Lnet/minecraft/commands/arguments/EntityAnchorArgument$Anchor;Lnet/minecraft/world/phys/Vec3;)V",
                    "lookAt(Lnet/minecraft/commands/arguments/EntityAnchorArgument$Anchor;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/commands/arguments/EntityAnchorArgument$Anchor;)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"
            )
    )
    private void virtualizeLookAtPacket(
            ServerGamePacketListenerImpl connection,
            Packet<?> packet,
            Operation<Void> original) {
        if (packet instanceof ClientboundPlayerLookAtPacket lookAt) {
            original.call(connection, virtualizeLookAt(lookAt, connection.player));
            return;
        }

        original.call(connection, packet);
    }

    @WrapOperation(
            method = "openTextEdit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"
            )
    )
    private void virtualizeSignEditorPacket(
            ServerGamePacketListenerImpl connection,
            Packet<?> packet,
            Operation<Void> original) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (packet instanceof ClientboundBlockUpdatePacket) {
            original.call(connection, BlockPacketUtil.virtualizeFor(packet, player));
            return;
        }
        if (packet instanceof ClientboundOpenSignEditorPacket signEditor) {
            BlockPos virtualPos = WorldEventPacketUtil.virtualizeBlockPos(player.level(), signEditor.getPos(), player);
            if (virtualPos.equals(signEditor.getPos())) {
                original.call(connection, packet);
            } else {
                original.call(connection, new ClientboundOpenSignEditorPacket(virtualPos, signEditor.isFrontText()));
            }
            return;
        }

        original.call(connection, packet);
    }

    private static ClientboundPlayerLookAtPacket virtualizeLookAt(
            ClientboundPlayerLookAtPacket packet,
            ServerPlayer player) {
        ClientboundPlayerLookAtPacketAccessor access = (ClientboundPlayerLookAtPacketAccessor) packet;
        Vec3 pos = new Vec3(access.globeWorld$getX(), access.globeWorld$getY(), access.globeWorld$getZ());
        Vec3 virtualPos = WorldEventPacketUtil.virtualizePos(player.level(), pos, player);
        if (virtualPos.x() == pos.x() && virtualPos.z() == pos.z()) {
            return packet;
        }

        if (!access.globeWorld$isAtEntity()) {
            return new ClientboundPlayerLookAtPacket(access.globeWorld$getFromAnchor(), virtualPos.x(), virtualPos.y(), virtualPos.z());
        }

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeEnum(access.globeWorld$getFromAnchor());
            buffer.writeDouble(virtualPos.x());
            buffer.writeDouble(virtualPos.y());
            buffer.writeDouble(virtualPos.z());
            buffer.writeBoolean(true);
            buffer.writeVarInt(access.globeWorld$getEntity());
            EntityAnchorArgument.Anchor toAnchor = access.globeWorld$getToAnchor();
            if (toAnchor == null) {
                return packet;
            }
            buffer.writeEnum(toAnchor);
            return ClientboundPlayerLookAtPacketAccessor.globeWorld$new(buffer);
        } finally {
            buffer.release();
        }
    }
}
