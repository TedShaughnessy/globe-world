package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.GlobeChunkPacket;
import globe.world.util.ChunkAliasTracker;
import globe.world.util.ChunkLoadDiagnostics;
import globe.world.util.CoordUtil;
import globe.world.util.WorldGenSpillover;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;

@Mixin(PlayerChunkSender.class)
public class PlayerChunkSenderMixin {
    @Shadow @Final private LongSet pendingChunks;
    @Shadow private int unacknowledgedBatches;
    @Shadow private int maxUnacknowledgedBatches;
    @Shadow private float desiredChunksPerTick;
    @Shadow private float batchQuota;

    @Inject(method = "sendNextChunks", at = @At("HEAD"))
    private void logPendingChunkSender(ServerPlayer player, CallbackInfo ci) {
        ChunkLoadDiagnostics.senderState(
                player,
                this.pendingChunks.size(),
                this.unacknowledgedBatches,
                this.maxUnacknowledgedBatches,
                this.desiredChunksPerTick,
                this.batchQuota
        );
    }

    @Inject(
        method = "sendChunk(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void waitForCanonicalAliasSource(
            ServerGamePacketListenerImpl connection,
            ServerLevel level,
            LevelChunk chunk,
            CallbackInfo ci) {
        int cx = chunk.getPos().x(), cz = chunk.getPos().z();
        int wcx = CoordUtil.wrapChunk(level, cx), wcz = CoordUtil.wrapChunk(level, cz);
        if (wcx == cx && wcz == cz) {
            return;
        }

        ChunkPos aliasPos = new ChunkPos(cx, cz);
        ChunkPos canonicalPos = new ChunkPos(wcx, wcz);
        if (level.getChunkSource().getChunkNow(wcx, wcz) == null) {
            ChunkLoadDiagnostics.blockedAliasSend(connection.player, level, aliasPos, canonicalPos);
            connection.chunkSender.markChunkPendingToSend(chunk);
            ci.cancel();
        }
    }

    @Inject(
        method = "sendChunk(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At("TAIL")
    )
    private static void refreshEntityTrackingAfterChunkSend(
            ServerGamePacketListenerImpl connection,
            ServerLevel level,
            LevelChunk chunk,
            CallbackInfo ci) {
        int cx = chunk.getPos().x(), cz = chunk.getPos().z();
        int wcx = CoordUtil.wrapChunk(level, cx), wcz = CoordUtil.wrapChunk(level, cz);
        if (wcx != cx || wcz != cz) {
            level.getChunkSource().chunkMap.move(connection.player);
        }
    }

    // Each non-canonical alias is sent at its raw position as the virtual coord.
    // Multiple aliases of the same canonical coexist on the client — this is
    // intentional and necessary for the tiling illusion as the player moves.
    @WrapOperation(
        method = "sendChunk(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket")
    )
    private static ClientboundLevelChunkWithLightPacket relabelChunkPacket(
            LevelChunk chunk, LevelLightEngine lightEngine, BitSet bs1, BitSet bs2,
            Operation<ClientboundLevelChunkWithLightPacket> original,
            @Local(argsOnly = true) ServerGamePacketListenerImpl conn,
            @Local(argsOnly = true) ServerLevel level) {
        int cx = chunk.getPos().x(), cz = chunk.getPos().z();
        int wcx = CoordUtil.wrapChunk(level, cx), wcz = CoordUtil.wrapChunk(level, cz);

        LevelChunk chunkToSend = chunk;

        if (wcx != cx || wcz != cz) {
            LevelChunk canonical = level.getChunkSource().getChunkNow(wcx, wcz);
            if (canonical != null) {
                ChunkLoadDiagnostics.recoveredAliasSend(conn.player, level, new ChunkPos(cx, cz), new ChunkPos(wcx, wcz));
                chunkToSend = canonical;
            }
        }

        ChunkAliasTracker.addAlias(conn.player, wcx, wcz, cx, cz);

        // Virtual coord = raw coord; client stores each alias at its natural position.
        // Multiple aliases of the same canonical chunk may coexist in the view,
        // which is required for tiny tiles where the render distance spans many wraps.
        WorldGenSpillover.applyToChunk(level, chunkToSend);
        ClientboundLevelChunkWithLightPacket packet = original.call(chunkToSend, lightEngine, bs1, bs2);
        if (cx != packet.getX() || cz != packet.getZ()) {
            ((GlobeChunkPacket) packet).setVirtualPos(cx, cz);
        }
        return packet;
    }

    @WrapOperation(
        method = "dropChunk",
        at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ClientboundForgetLevelChunkPacket")
    )
    private ClientboundForgetLevelChunkPacket relabelDropPacket(
            ChunkPos pos,
            Operation<ClientboundForgetLevelChunkPacket> original,
            @Local(argsOnly = true) ServerPlayer player) {
        int cx = pos.x(), cz = pos.z();
        int wcx = CoordUtil.wrapChunk(player.level(), cx), wcz = CoordUtil.wrapChunk(player.level(), cz);

        ChunkAliasTracker.removeAlias(player, wcx, wcz, cx, cz);

        // Drop uses the raw position — matches the virtual coord used at send time.
        return original.call(pos);
    }
}
