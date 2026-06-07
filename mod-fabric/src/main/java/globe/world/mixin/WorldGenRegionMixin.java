package globe.world.mixin;

import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import globe.world.util.WorldGenSpillover;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
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
    private StaticCache2D<GenerationChunkHolder> cache;

    @Shadow
    @Final
    private ChunkStep generatingStep;

    @Shadow
    @Final
    private ServerLevel level;

    @ModifyVariable(method = "getBlockState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenGetBlockStatePos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(this.level, pos);
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
            if (this.cache.contains(virtualX, virtualZ)) {
                cir.setReturnValue(((WorldGenRegion) (Object) this).getChunk(virtualX, virtualZ, targetStatus, loadOrGenerate));
            } else if (!loadOrGenerate) {
                cir.setReturnValue(null);
            }
        }
    }

    @Inject(method = "hasChunk", at = @At("HEAD"), cancellable = true)
    private void virtualizeWorldgenHasChunk(int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
        int virtualX = virtualCacheChunkX(chunkX);
        int virtualZ = virtualCacheChunkZ(chunkZ);
        if (virtualX != chunkX || virtualZ != chunkZ) {
            cir.setReturnValue(this.cache.contains(virtualX, virtualZ) && ((WorldGenRegion) (Object) this).hasChunk(virtualX, virtualZ));
        }
    }

    @ModifyVariable(method = "getFluidState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenGetFluidStatePos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(this.level, pos);
    }

    @ModifyVariable(method = "getBlockEntity", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenGetBlockEntityPos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(this.level, pos);
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void skipUnavailableCanonicalBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        BlockPos wrapped = CoordUtil.wrapBlockPos(this.level, pos);
        if (!physicalCacheContains(wrapped)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "ensureCanWrite", at = @At("HEAD"), cancellable = true)
    private void allowCanonicalWorldgenWrite(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockPos wrapped = CoordUtil.wrapBlockPos(this.level, pos);

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

    private int canonicalChunkDistance(int a, int b) {
        return CoordUtil.wrappedChunkDistance(DimensionTiling.forLevel(this.level), a, b);
    }

    private int virtualCacheChunkX(int chunkX) {
        return CoordUtil.virtualChunk(this.level, CoordUtil.wrapChunk(this.level, chunkX), this.center.getPos().x());
    }

    private int virtualCacheChunkZ(int chunkZ) {
        return CoordUtil.virtualChunk(this.level, CoordUtil.wrapChunk(this.level, chunkZ), this.center.getPos().z());
    }

    @ModifyVariable(method = "ensureCanWrite", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenEnsureCanWritePos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(this.level, pos);
    }

    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void setCanonicalWorldgenBlock(
            BlockPos pos,
            BlockState blockState,
            int updateFlags,
            int updateLimit,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockPos wrapped = CoordUtil.wrapBlockPos(this.level, pos);
        if (wrapped != pos) {
            WorldGenRegion region = (WorldGenRegion) (Object) this;
            if (!region.ensureCanWrite(wrapped)) {
                cir.setReturnValue(false);
                return;
            }

            if (!physicalCacheContains(wrapped)) {
                WorldGenSpillover.enqueue(this.level, wrapped, null, blockState, updateFlags);
                cir.setReturnValue(true);
                return;
            }

            BlockState expectedState = region.getBlockState(wrapped);
            boolean placed = region.setBlock(wrapped, blockState, updateFlags, updateLimit);
            if (placed) {
                WorldGenSpillover.enqueue(this.level, wrapped, expectedState, blockState, updateFlags);
            }
            cir.setReturnValue(placed);
        }
    }

    @ModifyVariable(method = "setBlock", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenSetBlockPos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(this.level, pos);
    }

    private boolean physicalCacheContains(BlockPos pos) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        return this.cache.contains(virtualCacheChunkX(chunkX), virtualCacheChunkZ(chunkZ));
    }

    @ModifyVariable(method = "markPosForPostprocessing", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenPostProcessingPos(BlockPos pos) {
        return CoordUtil.wrapBlockPos(this.level, pos);
    }
}
