package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import globe.world.util.PlayerCanonicalizer;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerList.class)
public class PlayerListCanonicalPositionMixin {
    @WrapOperation(
        method = "placeNewPlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFF)V"
        )
    )
    private void canonicalizeLoadedPlayer(
            ServerGamePacketListenerImpl listener,
            double x,
            double y,
            double z,
            float yRot,
            float xRot,
            Operation<Void> original,
            Connection connection,
            ServerPlayer player,
            CommonListenerCookie cookie) {
        PlayerCanonicalizer.canonicalize(player);
        original.call(listener, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    @WrapOperation(
        method = "respawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;snapTo(DDDFF)V"
        )
    )
    private void canonicalizeRespawnedPlayer(
            ServerPlayer player,
            double x,
            double y,
            double z,
            float yRot,
            float xRot,
            Operation<Void> original) {
        double canonicalX = CoordUtil.wrapBlock(player.level(), x);
        double canonicalZ = CoordUtil.wrapBlock(player.level(), z);
        original.call(player, canonicalX, y, canonicalZ, yRot, xRot);
        player.syncPacketPositionCodec(canonicalX, y, canonicalZ);
    }
}
