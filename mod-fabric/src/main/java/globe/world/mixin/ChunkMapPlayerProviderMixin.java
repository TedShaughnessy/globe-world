package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkMap.class)
public class ChunkMapPlayerProviderMixin {

    @WrapOperation(
        method = "getPlayers",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"
        )
    )
    private boolean wrapTrackedPlayerLookup(
            ChunkMap chunkMap,
            ServerPlayer player,
            int chunkX,
            int chunkZ,
            Operation<Boolean> original) {
        TopologyContext topology = TopologyContexts.forLevel(player.level());
        ChunkPos virtualChunk = topology.virtualChunkForViewer(chunkX, chunkZ, player);
        int virtualX = virtualChunk.x();
        int virtualZ = virtualChunk.z();
        return original.call(chunkMap, player, virtualX, virtualZ);
    }

    @WrapOperation(
        method = "getPlayers",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;isChunkOnTrackedBorder(Lnet/minecraft/server/level/ServerPlayer;II)Z"
        )
    )
    private boolean wrapTrackedBorderPlayerLookup(
            ChunkMap chunkMap,
            ServerPlayer player,
            int chunkX,
            int chunkZ,
            Operation<Boolean> original) {
        TopologyContext topology = TopologyContexts.forLevel(player.level());
        ChunkPos virtualChunk = topology.virtualChunkForViewer(chunkX, chunkZ, player);
        int virtualX = virtualChunk.x();
        int virtualZ = virtualChunk.z();
        return original.call(chunkMap, player, virtualX, virtualZ);
    }
}
