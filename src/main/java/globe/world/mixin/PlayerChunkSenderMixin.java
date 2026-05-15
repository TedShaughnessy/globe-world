package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.util.CoordUtil;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.BitSet;

@Mixin(PlayerChunkSender.class)
public class PlayerChunkSenderMixin {

    @WrapOperation(
        method = "sendChunk(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket")
    )
    private static ClientboundLevelChunkWithLightPacket relabelChunkPacket(
            LevelChunk chunk, LevelLightEngine lightEngine, BitSet bs1, BitSet bs2,
            Operation<ClientboundLevelChunkWithLightPacket> original,
            @Local(argsOnly = true) ServerGamePacketListenerImpl conn) {
        ClientboundLevelChunkWithLightPacket packet = original.call(chunk, lightEngine, bs1, bs2);
        ChunkPos playerChunk = conn.player.chunkPosition();
        int cx = packet.getX();
        int cz = packet.getZ();
        int vx = CoordUtil.virtualChunk(cx, playerChunk.x());
        int vz = CoordUtil.virtualChunk(cz, playerChunk.z());
        if (vx != cx || vz != cz) {
            ((LevelChunkPacketAccess) packet).setX(vx);
            ((LevelChunkPacketAccess) packet).setZ(vz);
        }
        return packet;
    }

    @WrapOperation(
        method = "dropChunk",
        at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ClientboundForgetLevelChunkPacket")
    )
    private ClientboundForgetLevelChunkPacket relabelDropPacket(
            ChunkPos canonical,
            Operation<ClientboundForgetLevelChunkPacket> original,
            @Local(argsOnly = true) ServerPlayer player) {
        ChunkPos playerChunk = player.chunkPosition();
        int vx = CoordUtil.virtualChunk(canonical.x(), playerChunk.x());
        int vz = CoordUtil.virtualChunk(canonical.z(), playerChunk.z());
        ChunkPos target = (vx != canonical.x() || vz != canonical.z()) ? new ChunkPos(vx, vz) : canonical;
        return original.call(target);
    }
}
