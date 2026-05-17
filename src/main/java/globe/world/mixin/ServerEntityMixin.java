package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.util.EntityPacketUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

@Mixin(ServerEntity.class)
public class ServerEntityMixin {

    @WrapOperation(
        method = "sendPairingData",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"
        )
    )
    private void virtualizePairingPacket(
            Consumer<Packet<ClientGamePacketListener>> consumer,
            Object packet,
            Operation<Void> original,
            @Local(argsOnly = true) ServerPlayer viewer) {
        @SuppressWarnings("unchecked")
        Packet<? super ClientGamePacketListener> gamePacket = (Packet<? super ClientGamePacketListener>) packet;
        original.call(consumer, EntityPacketUtil.virtualizeFor(gamePacket, viewer));
    }
}
