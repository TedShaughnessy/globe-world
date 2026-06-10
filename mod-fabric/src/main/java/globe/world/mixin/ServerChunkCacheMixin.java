package globe.world.mixin;

import globe.world.util.CanonicalChunkTickets;
import globe.world.util.ChunkAliasTracker;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import globe.world.util.DimensionTiling;
import globe.world.util.GlobeDistanceCaps;
import globe.world.util.WorldGenSpillover;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheMixin {
    @Shadow @Final private ServerLevel level;

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/DistanceManager;updateSimulationDistance(I)V"
            )
    )
    private int capInitialSimulationDistance(int configuredDistance) {
        return GlobeDistanceCaps.effectiveSimulationDistance(DimensionTiling.forLevel(this.level), configuredDistance);
    }

    @ModifyArg(
            method = "setSimulationDistance",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/DistanceManager;updateSimulationDistance(I)V"
            )
    )
    private int capUpdatedSimulationDistance(int configuredDistance) {
        return GlobeDistanceCaps.effectiveSimulationDistance(DimensionTiling.forLevel(this.level), configuredDistance);
    }

    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    private void wrapGetChunk(int x, int z, ChunkStatus status, boolean create,
                               CallbackInfoReturnable<ChunkAccess> cir) {
        TopologyContext topology = TopologyContexts.forLevel(this.level);
        ChunkPos canonicalPos = topology.canonicalChunk(x, z);
        if (canonicalPos.x() != x || canonicalPos.z() != z) {
            cir.setReturnValue(((ServerChunkCache) (Object) this).getChunk(canonicalPos.x(), canonicalPos.z(), status, create));
        }
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void wrapGetChunkNow(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
        TopologyContext topology = TopologyContexts.forLevel(this.level);
        ChunkPos canonicalPos = topology.canonicalChunk(x, z);
        if (canonicalPos.x() != x || canonicalPos.z() != z) {
            cir.setReturnValue(((ServerChunkCache) (Object) this).getChunkNow(canonicalPos.x(), canonicalPos.z()));
        }
    }

    @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
    private void wrapBlockChanged(BlockPos pos, CallbackInfo ci) {
        BlockPos wrapped = TopologyContexts.forLevel(this.level).canonicalBlock(pos);
        if (!wrapped.equals(pos)) {
            ((ServerChunkCache) (Object) this).blockChanged(wrapped);
            ci.cancel();
        }
    }

    @Inject(method = "deactivateTicketsOnClosing", at = @At("HEAD"))
    private void clearCanonicalAliasTicketsOnClosing(CallbackInfo ci) {
        CanonicalChunkTickets.clearLevel(this.level);
        ChunkAliasTracker.clearLevel(this.level.dimension());
        WorldGenSpillover.clearLevel(this.level);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void clearCanonicalAliasTicketsOnClose(CallbackInfo ci) {
        CanonicalChunkTickets.clearLevel(this.level);
        ChunkAliasTracker.clearLevel(this.level.dimension());
        WorldGenSpillover.clearLevel(this.level);
    }
}
