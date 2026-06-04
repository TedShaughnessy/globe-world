package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.ChunkPacketUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkMap.class)
public class ChunkMapBiomeResendMixin {
    @WrapOperation(
            method = "lambda$resendBiomesForChunks$1",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"
            )
    )
    private static void virtualizeBiomeResendPacket(
            ServerGamePacketListenerImpl connection,
            Packet<?> packet,
            Operation<Void> original) {
        if (packet instanceof ClientboundChunksBiomesPacket biomePacket) {
            for (Packet<?> virtualPacket : ChunkPacketUtil.virtualizeBiomeResendForLoadedAliases(biomePacket, connection.player)) {
                original.call(connection, virtualPacket);
            }
            return;
        }

        original.call(connection, packet);
    }
}
