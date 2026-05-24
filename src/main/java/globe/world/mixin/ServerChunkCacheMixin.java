package globe.world.mixin;

import globe.world.util.CanonicalChunkTickets;
import globe.world.util.CoordUtil;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private DistanceManager distanceManager;

    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    private void wrapGetChunk(int x, int z, ChunkStatus status, boolean create,
                               CallbackInfoReturnable<ChunkAccess> cir) {
        int wx = CoordUtil.wrapChunk(x);
        int wz = CoordUtil.wrapChunk(z);
        if (wx != x || wz != z) {
            cir.setReturnValue(((ServerChunkCache) (Object) this).getChunk(wx, wz, status, create));
        }
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void wrapGetChunkNow(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
        int wx = CoordUtil.wrapChunk(x);
        int wz = CoordUtil.wrapChunk(z);
        if (wx != x || wz != z) {
            cir.setReturnValue(((ServerChunkCache) (Object) this).getChunkNow(wx, wz));
        }
    }

    @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
    private void wrapBlockChanged(BlockPos pos, CallbackInfo ci) {
        BlockPos wrapped = CoordUtil.wrapBlockPos(pos);
        if (!wrapped.equals(pos)) {
            ((ServerChunkCache) (Object) this).blockChanged(wrapped);
        }
    }

    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerChunkCache;tickChunks()V",
            shift = At.Shift.BEFORE
        )
    )
    private void refreshCanonicalAliasTicking(BooleanSupplier haveTime, boolean tickChunks, CallbackInfo ci) {
        if (tickChunks) {
            CanonicalChunkTickets.refreshSimulationStatuses(this.level, this.distanceManager);
        }
    }
}
