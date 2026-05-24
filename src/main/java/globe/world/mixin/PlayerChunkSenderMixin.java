package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.GlobeChunkPacket;
import globe.world.util.ChunkAliasTracker;
import globe.world.util.CoordUtil;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(PlayerChunkSender.class)
public class PlayerChunkSenderMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("globe-world/chunks");

    // Ref-count FORCED tickets per canonical chunk across all players and aliases.
    // Keeps canonical data in memory while any alias is loaded by any player.
    private static final ConcurrentHashMap<Long, AtomicInteger> FORCED_REFS = new ConcurrentHashMap<>();

    private static long canonKey(int cx, int cz) {
        return ((long) cx << 32) | ((long) cz & 0xFFFFFFFFL);
    }

    private static void acquireForcedTicket(ServerLevel level, int wcx, int wcz) {
        long key = canonKey(wcx, wcz);
        if (FORCED_REFS.computeIfAbsent(key, k -> new AtomicInteger(0)).getAndIncrement() == 0) {
            level.getChunkSource().addTicketWithRadius(TicketType.FORCED, new ChunkPos(wcx, wcz), 0);
        }
    }

    private static void releaseForcedTicket(ServerLevel level, int wcx, int wcz) {
        long key = canonKey(wcx, wcz);
        AtomicInteger ref = FORCED_REFS.get(key);
        if (ref == null) return;
        if (ref.decrementAndGet() <= 0) {
            FORCED_REFS.remove(key);
            level.getChunkSource().removeTicketWithRadius(TicketType.FORCED, new ChunkPos(wcx, wcz), 0);
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
        int wcx = CoordUtil.wrapChunk(cx), wcz = CoordUtil.wrapChunk(cz);

        LevelChunk chunkToSend = chunk;

        if (wcx != cx || wcz != cz) {
            acquireForcedTicket(level, wcx, wcz);

            LevelChunk canonical = level.getChunkSource().getChunkNow(wcx, wcz);
            if (canonical == null) {
                net.minecraft.world.level.chunk.ChunkAccess ca = level.getChunk(wcx, wcz);
                if (ca instanceof LevelChunk lc) canonical = lc;
                if (canonical == null)
                    LOGGER.error("canonical=({},{}) unavailable for alias raw=({},{})", wcx, wcz, cx, cz);
            }
            if (canonical != null) chunkToSend = canonical;
        }

        ChunkAliasTracker.addAlias(conn.player, wcx, wcz, cx, cz);

        // Virtual coord = raw coord; client stores each alias at its natural position.
        // The alias enters/exits the client's view as the player moves, just like any
        // vanilla chunk would.
        ClientboundLevelChunkWithLightPacket packet = original.call(chunkToSend, lightEngine, bs1, bs2);
        if (cx != packet.getX() || cz != packet.getZ()) {
            ((GlobeChunkPacket) packet).setVirtualPos(cx, cz);
        }
        LOGGER.info("SEND chunk raw=({},{}) wrap=({},{}) virtual=({},{}) player=({},{})",
                cx, cz, wcx, wcz, cx, cz,
                conn.player.chunkPosition().x(), conn.player.chunkPosition().z());
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
        int wcx = CoordUtil.wrapChunk(cx), wcz = CoordUtil.wrapChunk(cz);

        ChunkAliasTracker.removeAlias(player, wcx, wcz, cx, cz);
        if (wcx != cx || wcz != cz) {
            releaseForcedTicket((ServerLevel) player.level(), wcx, wcz);
        }

        LOGGER.info("DROP chunk raw=({},{}) wrap=({},{}) player=({},{})",
                cx, cz, wcx, wcz, player.chunkPosition().x(), player.chunkPosition().z());
        // Drop uses the raw position — matches the virtual coord used at send time.
        return original.call(pos);
    }
}
