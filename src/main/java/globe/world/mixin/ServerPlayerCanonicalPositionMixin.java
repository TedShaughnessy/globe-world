package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.ChunkAliasTracker;
import globe.world.util.PlayerCanonicalizer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public class ServerPlayerCanonicalPositionMixin {
    @Inject(
        method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
        at = @At("HEAD")
    )
    private void clearChunkAliasesOnDimensionTransfer(
            TeleportTransition transition,
            CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        ResourceKey<Level> oldDimension = player.level().dimension();
        ResourceKey<Level> newDimension = transition.newLevel().dimension();
        if (!oldDimension.equals(newDimension)) {
            ChunkAliasTracker.clearPlayerDimension(player, oldDimension);
        }
    }

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
