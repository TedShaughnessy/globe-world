package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.GlobeChunkPacket;
import globe.world.config.GlobeConfig;
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
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

@Mixin(PlayerChunkSender.class)
public class PlayerChunkSenderMixin {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("globe-world/chunks");


    @WrapOperation(
        method = "sendChunk(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket")
    )
    private static ClientboundLevelChunkWithLightPacket relabelChunkPacket(
            LevelChunk chunk, LevelLightEngine lightEngine, BitSet bs1, BitSet bs2,
            Operation<ClientboundLevelChunkWithLightPacket> original,
            @Local(argsOnly = true) ServerGamePacketListenerImpl conn,
            @Local(argsOnly = true) ServerLevel level) {
        int cx = chunk.getPos().x();
        int cz = chunk.getPos().z();
        int wcx = CoordUtil.wrapChunk(cx);
        int wcz = CoordUtil.wrapChunk(cz);

        LevelChunk chunkToSend = chunk;
        int vx, vz;

        if (wcx != cx || wcz != cz) {
            // Non-canonical chunk. Add a persistent ticket so the canonical chunk is not
            // unloaded by the distance manager (canonical coords are far from the player's
            // virtual/raw position, so they get no natural player ticket).
            level.getChunkSource().addTicketWithRadius(TicketType.FORCED, new ChunkPos(wcx, wcz), 0);

            LevelChunk canonical = level.getChunkSource().getChunkNow(wcx, wcz);
            if (canonical == null) {
                net.minecraft.world.level.chunk.ChunkAccess ca = level.getChunk(wcx, wcz);
                if (ca instanceof LevelChunk lc) canonical = lc;
            }
            if (canonical != null) chunkToSend = canonical;
            vx = cx;
            vz = cz;
        } else {
            // Canonical chunk: map to nearest virtual alias, then send extra packets for every
            // other alias within view distance so that tiles beyond the first are also filled.
            ChunkPos playerChunk = conn.player.chunkPosition();
            vx = CoordUtil.virtualChunk(cx, playerChunk.x());
            vz = CoordUtil.virtualChunk(cz, playerChunk.z());

            int viewDist = level.getServer().getPlayerList().getViewDistance();
            List<Integer> xAliases = visibleAliases(cx, playerChunk.x(), viewDist);
            List<Integer> zAliases = visibleAliases(cz, playerChunk.z(), viewDist);
            for (int ax : xAliases) {
                for (int az : zAliases) {
                    if (ax == vx && az == vz) continue;
                    ClientboundLevelChunkWithLightPacket extra = original.call(chunkToSend, lightEngine, bs1, bs2);
                    ((GlobeChunkPacket) extra).setVirtualPos(ax, az);
                    conn.send(extra);
                }
            }
        }

        ClientboundLevelChunkWithLightPacket packet = original.call(chunkToSend, lightEngine, bs1, bs2);
        boolean relabeled = vx != packet.getX() || vz != packet.getZ();
        if (relabeled) {
            ((GlobeChunkPacket) packet).setVirtualPos(vx, vz);
        }
        LOGGER.info("SEND chunk raw=({},{}) wrap=({},{}) virtual=({},{}) relabeled={} player=({},{})",
                cx, cz, wcx, wcz, vx, vz, relabeled,
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
        int cx = pos.x();
        int cz = pos.z();
        int wcx = CoordUtil.wrapChunk(cx);
        int wcz = CoordUtil.wrapChunk(cz);

        int vx, vz;
        if (wcx != cx || wcz != cz) {
            // Release the canonical hold ticket this virtual chunk was keeping alive.
            ServerLevel sLevel = (ServerLevel) player.level();
            sLevel.getChunkSource().removeTicketWithRadius(TicketType.FORCED, new ChunkPos(wcx, wcz), 0);
            vx = cx;
            vz = cz;
        } else {
            ChunkPos playerChunk = player.chunkPosition();
            vx = CoordUtil.virtualChunk(cx, playerChunk.x());
            vz = CoordUtil.virtualChunk(cz, playerChunk.z());

            // Drop every alias that was sent at chunk-load time.
            int viewDist = ((ServerLevel) player.level()).getServer().getPlayerList().getViewDistance();
            List<Integer> xAliases = visibleAliases(cx, playerChunk.x(), viewDist);
            List<Integer> zAliases = visibleAliases(cz, playerChunk.z(), viewDist);
            for (int ax : xAliases) {
                for (int az : zAliases) {
                    if (ax == vx && az == vz) continue;
                    player.connection.send(original.call(new ChunkPos(ax, az)));
                }
            }
        }

        ChunkPos target = (vx != cx || vz != cz) ? new ChunkPos(vx, vz) : pos;
        return original.call(target);
    }

    // All virtual aliases of `canonical` that fall within viewDist of playerCoord.
    private static List<Integer> visibleAliases(int canonical, int playerCoord, int viewDist) {
        List<Integer> result = new ArrayList<>();
        int k0 = Math.floorDiv(playerCoord - viewDist - canonical, GlobeConfig.W_CHUNKS);
        for (int k = k0; ; k++) {
            int alias = canonical + k * GlobeConfig.W_CHUNKS;
            if (alias > playerCoord + viewDist) break;
            if (alias >= playerCoord - viewDist) result.add(alias);
        }
        return result;
    }
}
