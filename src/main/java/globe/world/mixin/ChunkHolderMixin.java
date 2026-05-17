package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.BlockPacketUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkHolder.class)
public class ChunkHolderMixin {

    @WrapOperation(
        method = "lambda$broadcast$0",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private static void virtualizeBlockUpdatePacket(
            ServerGamePacketListenerImpl connection,
            Packet<?> packet,
            Operation<Void> original,
            Packet<?> originalPacket,
            ServerPlayer player) {
        original.call(connection, BlockPacketUtil.virtualizeFor(packet, player));
    }
}
