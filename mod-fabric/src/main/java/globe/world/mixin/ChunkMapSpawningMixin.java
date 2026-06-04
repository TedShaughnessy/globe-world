package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public class ChunkMapSpawningMixin {
    @Shadow @Final private ServerLevel level;

    @Unique
    private final Set<Long> globeWorld$spawningCanonicalChunks = new HashSet<>();

    @Inject(method = "collectSpawningChunks", at = @At("HEAD"))
    private void beginCanonicalSpawningChunkPass(List<LevelChunk> output, CallbackInfo ci) {
        globeWorld$spawningCanonicalChunks.clear();
    }

    @WrapOperation(
        method = "collectSpawningChunks",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;add(Ljava/lang/Object;)Z"
        )
    )
    private boolean addCanonicalSpawningChunkOnce(
            List<LevelChunk> output,
            Object chunkObject,
            Operation<Boolean> original) {
        LevelChunk chunk = (LevelChunk) chunkObject;
        ChunkPos canonicalPos = CoordUtil.wrapChunkPos(this.level, chunk.getPos());
        if (!globeWorld$spawningCanonicalChunks.add(canonicalPos.pack())) {
            return false;
        }

        LevelChunk canonicalChunk = chunk;
        if (!canonicalPos.equals(chunk.getPos())) {
            canonicalChunk = this.level.getChunkSource().getChunkNow(canonicalPos.x(), canonicalPos.z());
            if (canonicalChunk == null) {
                return false;
            }
        }

        return original.call(output, canonicalChunk);
    }

    @Inject(method = "collectSpawningChunks", at = @At("RETURN"))
    private void endCanonicalSpawningChunkPass(List<LevelChunk> output, CallbackInfo ci) {
        globeWorld$spawningCanonicalChunks.clear();
    }

    @WrapOperation(
        method = "playerIsCloseEnoughForSpawning",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;euclideanDistanceSquared(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    private double wrapSpawningChunkDistance(ChunkPos chunkPos, Vec3 playerPos, Operation<Double> original) {
        return CoordUtil.wrappedChunkDistanceSqr(this.level, chunkPos, playerPos);
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
        int virtualX = CoordUtil.virtualChunk(player.level(), CoordUtil.wrapChunk(player.level(), chunkX), playerChunk.x());
        int virtualZ = CoordUtil.virtualChunk(player.level(), CoordUtil.wrapChunk(player.level(), chunkZ), playerChunk.z());
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
        int virtualX = CoordUtil.virtualChunk(player.level(), CoordUtil.wrapChunk(player.level(), chunkX), playerChunk.x());
        int virtualZ = CoordUtil.virtualChunk(player.level(), CoordUtil.wrapChunk(player.level(), chunkZ), playerChunk.z());
        return original.call(chunkMap, player, virtualX, virtualZ);
    }
}
