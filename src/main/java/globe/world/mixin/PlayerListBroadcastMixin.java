package globe.world.mixin;

import globe.world.util.DimensionTiling;
import globe.world.util.WorldEventPacketUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PlayerList.class)
public class PlayerListBroadcastMixin {
    @Shadow
    @Final
    private List<ServerPlayer> players;

    @Inject(method = "broadcast", at = @At("HEAD"), cancellable = true)
    private void broadcastWorldEventWithWrappedDistance(
            Player except,
            double x,
            double y,
            double z,
            double range,
            ResourceKey<Level> dimension,
            Packet<?> packet,
            CallbackInfo ci) {
        if (!DimensionTiling.forDimension(dimension).enabled() || !WorldEventPacketUtil.handles(packet)) {
            return;
        }

        Vec3 source = new Vec3(x, y, z);
        double rangeSqr = range * range;
        for (ServerPlayer player : this.players) {
            if (player != except && player.level().dimension().equals(dimension)) {
                ServerLevel level = player.level();
                if (WorldEventPacketUtil.wrappedDistanceSqr(level, source, player) < rangeSqr) {
                    player.connection.send(WorldEventPacketUtil.virtualizeFor(packet, player));
                }
            }
        }
        ci.cancel();
    }
}
