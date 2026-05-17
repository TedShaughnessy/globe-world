package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
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
        ChunkPos playerChunk = player.chunkPosition();
        int virtualX = CoordUtil.virtualChunk(CoordUtil.wrapChunk(chunkX), playerChunk.x());
        int virtualZ = CoordUtil.virtualChunk(CoordUtil.wrapChunk(chunkZ), playerChunk.z());
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
        ChunkPos playerChunk = player.chunkPosition();
        int virtualX = CoordUtil.virtualChunk(CoordUtil.wrapChunk(chunkX), playerChunk.x());
        int virtualZ = CoordUtil.virtualChunk(CoordUtil.wrapChunk(chunkZ), playerChunk.z());
        return original.call(chunkMap, player, virtualX, virtualZ);
    }
}
