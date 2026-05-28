package globe.world.mixin;

import globe.world.util.CanonicalChunkTickets;
import globe.world.util.CoordUtil;
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

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    private void wrapGetChunk(int x, int z, ChunkStatus status, boolean create,
                               CallbackInfoReturnable<ChunkAccess> cir) {
        int wx = CoordUtil.wrapChunk(this.level, x);
        int wz = CoordUtil.wrapChunk(this.level, z);
        if (wx != x || wz != z) {
            cir.setReturnValue(((ServerChunkCache) (Object) this).getChunk(wx, wz, status, create));
        }
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void wrapGetChunkNow(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
        int wx = CoordUtil.wrapChunk(this.level, x);
        int wz = CoordUtil.wrapChunk(this.level, z);
        if (wx != x || wz != z) {
            cir.setReturnValue(((ServerChunkCache) (Object) this).getChunkNow(wx, wz));
        }
    }

    @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
    private void wrapBlockChanged(BlockPos pos, CallbackInfo ci) {
        BlockPos wrapped = CoordUtil.wrapBlockPos(this.level, pos);
        if (!wrapped.equals(pos)) {
            ((ServerChunkCache) (Object) this).blockChanged(wrapped);
            ci.cancel();
        }
    }

    @Inject(method = "deactivateTicketsOnClosing", at = @At("HEAD"))
    private void clearCanonicalAliasTicketsOnClosing(CallbackInfo ci) {
        CanonicalChunkTickets.clearLevel(this.level);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void clearCanonicalAliasTicketsOnClose(CallbackInfo ci) {
        CanonicalChunkTickets.clearLevel(this.level);
    }
}
