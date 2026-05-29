package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import globe.world.util.EntityPacketUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class ChunkMapTrackedEntityMixin {

    @Shadow
    @Final
    private Entity entity;

    @Shadow
    @Final
    private Set<ServerPlayerConnection> seenBy;

    @Unique
    private final Map<UUID, Long> globeWorld$lastVirtualChunkByPlayer = new HashMap<>();

    @WrapOperation(
        method = "updatePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private Vec3 wrapTrackingDelta(Vec3 playerPos, Vec3 entityPos, Operation<Vec3> original) {
        Vec3 delta = original.call(playerPos, entityPos);
        return new Vec3(
            CoordUtil.wrappedDeltaBlock(this.entity.level(), playerPos.x, entityPos.x),
            delta.y,
            CoordUtil.wrappedDeltaBlock(this.entity.level(), playerPos.z, entityPos.z)
        );
    }

    @WrapOperation(
        method = "updatePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"
        )
    )
    private boolean wrapTrackedEntityChunkLookup(
            ChunkMap chunkMap,
            ServerPlayer player,
            int chunkX,
            int chunkZ,
            Operation<Boolean> original) {
        ChunkPos playerChunk = player.chunkPosition();
        int virtualX = CoordUtil.virtualChunk(player.level(), CoordUtil.wrapChunk(player.level(), chunkX), playerChunk.x());
        int virtualZ = CoordUtil.virtualChunk(player.level(), CoordUtil.wrapChunk(player.level(), chunkZ), playerChunk.z());
        if (virtualX != chunkX || virtualZ != chunkZ) {
            return player.getChunkTrackingView().contains(virtualX, virtualZ);
        }
        return original.call(chunkMap, player, virtualX, virtualZ);
    }

    @Inject(method = "updatePlayer", at = @At("TAIL"))
    private void resyncNearestAliasWhenVisibleCopyChanges(ServerPlayer player, CallbackInfo ci) {
        if (!this.seenBy.contains(player.connection)) {
            this.globeWorld$lastVirtualChunkByPlayer.remove(player.getUUID());
            return;
        }

        ChunkPos playerChunk = player.chunkPosition();
        int virtualX = CoordUtil.virtualChunk(
                player.level(),
                CoordUtil.wrapChunk(player.level(), this.entity.chunkPosition().x()),
                playerChunk.x()
        );
        int virtualZ = CoordUtil.virtualChunk(
                player.level(),
                CoordUtil.wrapChunk(player.level(), this.entity.chunkPosition().z()),
                playerChunk.z()
        );
        long virtualChunk = new ChunkPos(virtualX, virtualZ).pack();
        Long previous = this.globeWorld$lastVirtualChunkByPlayer.put(player.getUUID(), virtualChunk);
        if (previous != null && previous != virtualChunk) {
            Packet<? super ClientGamePacketListener> packet = ClientboundEntityPositionSyncPacket.of(this.entity);
            player.connection.send(EntityPacketUtil.virtualizeFor(packet, player));
        }
    }

    @WrapOperation(
        method = {
            "sendToTrackingPlayers",
            "sendToTrackingPlayersFiltered"
        },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerConnection;send(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private void virtualizeTrackedPacket(
            ServerPlayerConnection connection,
            Packet<? super ClientGamePacketListener> packet,
            Operation<Void> original) {
        original.call(connection, EntityPacketUtil.virtualizeFor(packet, connection.getPlayer()));
    }

    @WrapOperation(
        method = "sendToTrackingPlayersAndSelf",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private void virtualizeSelfPacket(
            ServerGamePacketListenerImpl connection,
            Packet<? super ClientGamePacketListener> packet,
            Operation<Void> original) {
        if (entity instanceof ServerPlayer player) {
            original.call(connection, EntityPacketUtil.virtualizeFor(packet, player));
            return;
        }
        original.call(connection, packet);
    }
}
