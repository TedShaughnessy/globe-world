package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.DimensionTiling;
import globe.world.util.WorldEventPacketUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public class ServerLevelWorldEventMixin {
    @Inject(method = "destroyBlockProgress", at = @At("HEAD"), cancellable = true)
    private void destroyBlockProgressWithWrappedDistance(
            int id,
            BlockPos blockPos,
            int progress,
            CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!DimensionTiling.forLevel(level).enabled()) {
            return;
        }

        Vec3 source = new Vec3(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        Packet<?> packet = new ClientboundBlockDestructionPacket(id, blockPos, progress);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() == level && player.getId() != id
                    && WorldEventPacketUtil.wrappedDistanceSqr(level, source, player) < 1024.0) {
                player.connection.send(WorldEventPacketUtil.virtualizeFor(packet, player));
            }
        }
        ci.cancel();
    }

    @Inject(
            method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sendParticlesWithWrappedDistance(
            ServerPlayer player,
            boolean overrideLimiter,
            double x,
            double y,
            double z,
            Packet<?> packet,
            CallbackInfoReturnable<Boolean> cir) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!DimensionTiling.forLevel(level).enabled()) {
            return;
        }

        if (player.level() != level) {
            cir.setReturnValue(false);
            return;
        }

        double range = overrideLimiter ? 512.0 : 32.0;
        Vec3 source = new Vec3(x, y, z);
        Vec3 viewer = Vec3.atCenterOf(player.blockPosition());
        if (WorldEventPacketUtil.wrappedDistanceSqr(level, source, viewer) < range * range) {
            player.connection.send(WorldEventPacketUtil.virtualizeFor(packet, player));
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "globalLevelEvent", at = @At("HEAD"), cancellable = true)
    private void globalLevelEventWithWrappedDirection(
            int type,
            BlockPos pos,
            int data,
            CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!DimensionTiling.forLevel(level).enabled()
                || !level.getGameRules().get(GameRules.GLOBAL_SOUND_EVENTS)) {
            return;
        }

        Vec3 centerOfBlock = Vec3.atCenterOf(pos);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            Vec3 soundPos;
            if (player.level() == level) {
                Vec3 virtualCenter = WorldEventPacketUtil.virtualizePos(level, centerOfBlock, player);
                if (WorldEventPacketUtil.wrappedDistanceSqr(level, centerOfBlock, player) < 1024.0) {
                    soundPos = virtualCenter;
                } else {
                    Vec3 directionToEvent = virtualCenter.subtract(player.position()).normalize();
                    soundPos = player.position().add(directionToEvent.scale(32.0));
                }
            } else {
                soundPos = player.position();
            }

            player.connection.send(new ClientboundLevelEventPacket(type, BlockPos.containing(soundPos), data, true));
        }
        ci.cancel();
    }

    @WrapOperation(
            method = "explode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
            )
    )
    private double explodeWithWrappedDistance(ServerPlayer player, Vec3 center, Operation<Double> original) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!DimensionTiling.forLevel(level).enabled()) {
            return original.call(player, center);
        }
        return WorldEventPacketUtil.wrappedDistanceSqr(level, center, player);
    }

    @WrapOperation(
            method = "explode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"
            )
    )
    private void sendVirtualExplosionPacket(
            ServerGamePacketListenerImpl connection,
            Packet<?> packet,
            Operation<Void> original) {
        original.call(connection, WorldEventPacketUtil.virtualizeFor(packet, connection.player));
    }
}
