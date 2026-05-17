package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkMap.class)
public class ChunkMapSpawningMixin {

    @WrapOperation(
        method = "playerIsCloseEnoughForSpawning",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;euclideanDistanceSquared(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    private double wrapSpawningChunkDistance(ChunkPos chunkPos, Vec3 playerPos, Operation<Double> original) {
        return CoordUtil.wrappedChunkDistanceSqr(chunkPos, playerPos);
    }

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
