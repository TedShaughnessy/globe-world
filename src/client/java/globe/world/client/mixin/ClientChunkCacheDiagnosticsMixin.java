package globe.world.client.mixin;

import globe.world.GlobeWorld;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.Consumer;

@Mixin(ClientChunkCache.class)
public class ClientChunkCacheDiagnosticsMixin {
    @Unique private int globeWorld$viewCenterX;
    @Unique private int globeWorld$viewCenterZ;
    @Unique private int globeWorld$storageRadius;
    @Unique private int globeWorld$acceptedChunkPackets;
    @Unique private int globeWorld$ignoredChunkPackets;

    @Inject(method = "updateViewCenter", at = @At("RETURN"))
    private void logClientViewCenter(int x, int z, CallbackInfo ci) {
        this.globeWorld$viewCenterX = x;
        this.globeWorld$viewCenterZ = z;
        GlobeWorld.LOGGER.warn("GW_CLIENT_CHUNK_CENTER center={}", format(x, z));
    }

    @Inject(method = "updateViewRadius", at = @At("RETURN"))
    private void logClientViewRadius(int viewRange, CallbackInfo ci) {
        this.globeWorld$storageRadius = Math.max(2, viewRange) + 3;
        GlobeWorld.LOGGER.warn("GW_CLIENT_CHUNK_RADIUS viewRange={} storageRadius={}", viewRange, this.globeWorld$storageRadius);
    }

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void logClientChunkPacket(
            int chunkX,
            int chunkZ,
            FriendlyByteBuf readBuffer,
            Map<Heightmap.Types, long[]> heightmaps,
            Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities,
            CallbackInfoReturnable<LevelChunk> cir) {
        if (cir.getReturnValue() == null) {
            this.globeWorld$ignoredChunkPackets++;
            GlobeWorld.LOGGER.warn(
                    "GW_CLIENT_CHUNK_PACKET ignored count={} chunk={} center={}",
                    this.globeWorld$ignoredChunkPackets,
                    format(chunkX, chunkZ),
                    format(this.globeWorld$viewCenterX, this.globeWorld$viewCenterZ)
            );
            return;
        }

        this.globeWorld$acceptedChunkPackets++;
        if (this.globeWorld$acceptedChunkPackets <= 8 || isNearViewEdge(chunkX, chunkZ)) {
            GlobeWorld.LOGGER.warn(
                    "GW_CLIENT_CHUNK_PACKET accepted count={} chunk={} center={}",
                    this.globeWorld$acceptedChunkPackets,
                    format(chunkX, chunkZ),
                    format(this.globeWorld$viewCenterX, this.globeWorld$viewCenterZ)
            );
        }
    }

    @Unique
    private boolean isNearViewEdge(int chunkX, int chunkZ) {
        int threshold = this.globeWorld$storageRadius > 0 ? Math.max(0, this.globeWorld$storageRadius - 2) : 27;
        return Math.abs(chunkX - this.globeWorld$viewCenterX) >= threshold
                || Math.abs(chunkZ - this.globeWorld$viewCenterZ) >= threshold;
    }

    @Unique
    private static String format(int x, int z) {
        return x + "," + z;
    }
}
