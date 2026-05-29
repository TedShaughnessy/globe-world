package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.PlayerCanonicalizer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public class ServerPlayerCanonicalPositionMixin {
    @WrapOperation(
        method = "stopSleepInBed",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFF)V"
        )
    )
    private void canonicalizeWakingPlayer(
            ServerGamePacketListenerImpl listener,
            double x,
            double y,
            double z,
            float yRot,
            float xRot,
            Operation<Void> original) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        PlayerCanonicalizer.canonicalize(player);
        original.call(listener, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }
}
