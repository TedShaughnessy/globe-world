package globe.world.mixin;

import globe.world.util.CoordUtil;
import globe.world.config.GlobeConfig;
import globe.world.util.WorldGenSpillover;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
public class WorldGenRegionMixin {
    @Shadow
    @Final
    private ChunkAccess center;

    @Shadow
    @Final
    private ChunkStep generatingStep;

    @Shadow
    @Final
    private ServerLevel level;

    @ModifyVariable(method = "getBlockState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenGetBlockStatePos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(pos);
    }

    @Inject(
            method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void virtualizeWorldgenChunkLookup(
            int chunkX,
            int chunkZ,
            net.minecraft.world.level.chunk.status.ChunkStatus targetStatus,
            boolean loadOrGenerate,
            CallbackInfoReturnable<ChunkAccess> cir
    ) {
        int virtualX = virtualCacheChunkX(chunkX);
        int virtualZ = virtualCacheChunkZ(chunkZ);
        if (virtualX != chunkX || virtualZ != chunkZ) {
            cir.setReturnValue(((WorldGenRegion) (Object) this).getChunk(virtualX, virtualZ, targetStatus, loadOrGenerate));
        }
    }

    @Inject(method = "hasChunk", at = @At("HEAD"), cancellable = true)
    private void virtualizeWorldgenHasChunk(int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
        int virtualX = virtualCacheChunkX(chunkX);
        int virtualZ = virtualCacheChunkZ(chunkZ);
        if (virtualX != chunkX || virtualZ != chunkZ) {
            cir.setReturnValue(((WorldGenRegion) (Object) this).hasChunk(virtualX, virtualZ));
        }
    }

    @ModifyVariable(method = "getFluidState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenGetFluidStatePos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(pos);
    }

    @ModifyVariable(method = "getBlockEntity", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenGetBlockEntityPos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(pos);
    }

    @Inject(method = "ensureCanWrite", at = @At("HEAD"), cancellable = true)
    private void allowCanonicalWorldgenWrite(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockPos wrapped = CoordUtil.wrapBlockPos(pos);

        int chunkX = SectionPos.blockToSectionCoord(wrapped.getX());
        int chunkZ = SectionPos.blockToSectionCoord(wrapped.getZ());
        ChunkPos centerPos = this.center.getPos();
        int distanceX = canonicalChunkDistance(centerPos.x(), chunkX);
        int distanceZ = canonicalChunkDistance(centerPos.z(), chunkZ);
        if (distanceX > this.generatingStep.blockStateWriteRadius()
                || distanceZ > this.generatingStep.blockStateWriteRadius()) {
            return;
        }

        if (this.center.isUpgrading()) {
            LevelHeightAccessor heightAccessor = this.center.getHeightAccessorForGeneration();
            if (heightAccessor.isOutsideBuildHeight(wrapped.getY())) {
                cir.setReturnValue(false);
                return;
            }
        }

        cir.setReturnValue(true);
    }

    private static int canonicalChunkDistance(int a, int b) {
        if (!GlobeConfig.enabled()) {
            return Math.abs(a - b);
        }

        int tileSize = GlobeConfig.tileSizeChunks();
        int wrappedDelta = Math.floorMod(a - b, tileSize);
        return Math.min(wrappedDelta, tileSize - wrappedDelta);
    }

    private int virtualCacheChunkX(int chunkX) {
        return CoordUtil.virtualChunk(CoordUtil.wrapChunk(chunkX), this.center.getPos().x());
    }

    private int virtualCacheChunkZ(int chunkZ) {
        return CoordUtil.virtualChunk(CoordUtil.wrapChunk(chunkZ), this.center.getPos().z());
    }

    @ModifyVariable(method = "ensureCanWrite", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenEnsureCanWritePos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(pos);
    }

    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void setCanonicalWorldgenBlock(
            BlockPos pos,
            BlockState blockState,
            int updateFlags,
            int updateLimit,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockPos wrapped = CoordUtil.wrapBlockPos(pos);
        if (wrapped != pos) {
            WorldGenSpillover.enqueue(this.level, wrapped, blockState, updateFlags);
            cir.setReturnValue(((WorldGenRegion)(Object)this).setBlock(wrapped, blockState, updateFlags, updateLimit));
        }
    }

    @ModifyVariable(method = "setBlock", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenSetBlockPos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(pos);
    }

    @ModifyVariable(method = "markPosForPostprocessing", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenPostProcessingPos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(pos);
    }
}
