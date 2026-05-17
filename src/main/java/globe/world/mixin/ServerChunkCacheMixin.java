package globe.world.mixin;

import globe.world.util.CoordUtil;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheMixin {

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
}
