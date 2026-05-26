package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.GlobeChunkPacket;
import globe.world.util.ChunkAliasTracker;
import globe.world.util.CoordUtil;
import globe.world.util.WorldGenSpillover;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;

@Mixin(PlayerChunkSender.class)
public class PlayerChunkSenderMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("globe-world/chunks");

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

        if (level.getChunkSource().getChunkNow(wcx, wcz) == null) {
            connection.chunkSender.markChunkPendingToSend(chunk);
            LOGGER.info("DEFER alias raw=({},{}) wrap=({},{}) waiting for canonical source", cx, cz, wcx, wcz);
            ci.cancel();
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
        ChunkPos playerChunk = conn.player.chunkPosition();
        int virtualX = CoordUtil.virtualChunk(level, wcx, playerChunk.x());
        int virtualZ = CoordUtil.virtualChunk(level, wcz, playerChunk.z());

        LevelChunk chunkToSend = chunk;

        if (wcx != cx || wcz != cz) {
            LevelChunk canonical = level.getChunkSource().getChunkNow(wcx, wcz);
            if (canonical == null) {
                LOGGER.error("canonical=({},{}) unavailable for alias raw=({},{})", wcx, wcz, cx, cz);
            }
            if (canonical != null) chunkToSend = canonical;
        }

        ChunkAliasTracker.addAlias(conn.player, wcx, wcz, virtualX, virtualZ);

        // Send at the player-nearest virtual coordinate. This also covers the
        // edge case where vanilla queues a canonical edge chunk while the
        // client is tracking its alias copy just across the tile boundary.
        WorldGenSpillover.applyToChunk(level, chunkToSend);
        ClientboundLevelChunkWithLightPacket packet = original.call(chunkToSend, lightEngine, bs1, bs2);
        if (virtualX != packet.getX() || virtualZ != packet.getZ()) {
            ((GlobeChunkPacket) packet).setVirtualPos(virtualX, virtualZ);
        }
        ((PlayerChunkSenderAccessor) conn.chunkSender).globeWorld$pendingChunks().remove(ChunkPos.pack(virtualX, virtualZ));
        LOGGER.info("SEND chunk raw=({},{}) wrap=({},{}) virtual=({},{}) player=({},{})",
                cx, cz, wcx, wcz, virtualX, virtualZ, playerChunk.x(), playerChunk.z());
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

        LOGGER.info("DROP chunk raw=({},{}) wrap=({},{}) player=({},{})",
                cx, cz, wcx, wcz, player.chunkPosition().x(), player.chunkPosition().z());
        // Drop uses the raw position — matches the virtual coord used at send time.
        return original.call(pos);
    }
}
