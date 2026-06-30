package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologyContexts;
import globe.world.util.ChunkAliasTracker;
import globe.world.util.PlayerCanonicalizer;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public class PlayerListCanonicalPositionMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void clearChunkAliasesOnRemove(ServerPlayer player, CallbackInfo ci) {
        ChunkAliasTracker.clearPlayer(player);
    }

    @Inject(method = "respawn", at = @At("HEAD"))
    private void clearChunkAliasesOnRespawn(
            ServerPlayer serverPlayer,
            boolean keepAllPlayerData,
            Entity.RemovalReason removalReason,
            CallbackInfoReturnable<ServerPlayer> cir) {
        ChunkAliasTracker.clearPlayer(serverPlayer);
    }

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
        Vec3 canonical = TopologyContexts.forLevel(player.level()).canonicalBlock(new Vec3(x, y, z));
        double canonicalX = canonical.x();
        double canonicalZ = canonical.z();
        original.call(player, canonicalX, y, canonicalZ, yRot, xRot);
        player.syncPacketPositionCodec(canonicalX, y, canonicalZ);
    }
}
