package globe.world.client.mixin;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.Consumer;

@Mixin(ClientChunkCache.class)
public class ClientChunkCacheMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("globe-world/client-chunks");

    @Inject(
        method = "replaceWithPacketData",
        at = @At("HEAD")
    )
    private void onChunkReceived(int x, int z, FriendlyByteBuf buf,
                                  Map<Heightmap.Types, long[]> heightmaps,
                                  Consumer consumer,
                                  CallbackInfoReturnable<LevelChunk> cir) {
        LOGGER.info("CLIENT_RECV chunk=({},{})", x, z);
    }

    @Inject(
        method = "replaceWithPacketData",
        at = @At("RETURN")
    )
    private void onChunkReceivedResult(int x, int z, FriendlyByteBuf buf,
                                        Map<Heightmap.Types, long[]> heightmaps,
                                        Consumer consumer,
                                        CallbackInfoReturnable<LevelChunk> cir) {
        if (cir.getReturnValue() == null) {
            LOGGER.warn("CLIENT_RECV_FAIL chunk=({},{}) — not stored (out of range or storage mismatch)", x, z);
        }
    }

    @Inject(
        method = "drop",
        at = @At("HEAD")
    )
    private void onChunkDropped(ChunkPos pos, CallbackInfo ci) {
        LOGGER.info("CLIENT_DROP chunk=({},{})", pos.x(), pos.z());
    }
}
