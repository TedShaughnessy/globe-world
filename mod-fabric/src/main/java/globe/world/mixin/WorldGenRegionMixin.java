package globe.world.mixin;

import globe.world.util.GenerationWindow;
import globe.world.util.WorldGenSpillover;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
        return usesVanillaRegionWindow() ? window().canonicalReadPos(pos) : pos;
    }

    @Inject(
            method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void virtualizeWorldgenChunkLookup(
            int chunkX,
            int chunkZ,
            ChunkStatus targetStatus,
            boolean loadOrGenerate,
            CallbackInfoReturnable<ChunkAccess> cir
    ) {
        if (!usesVanillaRegionWindow()) {
            return;
        }

        GenerationWindow window = window();
        GenerationWindow.ChunkLookup lookup = window.resolveChunk(chunkX, chunkZ, targetStatus, loadOrGenerate);
        window.logChunkLookup(lookup, targetStatus, loadOrGenerate);
        if (lookup.kind() == GenerationWindow.ChunkLookup.Kind.CACHE_ALIAS) {
            cir.setReturnValue(((WorldGenRegion) (Object) this).getChunk(
                    lookup.virtualChunkX(),
                    lookup.virtualChunkZ(),
                    targetStatus,
                    loadOrGenerate
            ));
        } else if (lookup.kind() == GenerationWindow.ChunkLookup.Kind.UNAVAILABLE_NO_LOAD) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "hasChunk", at = @At("HEAD"), cancellable = true)
    private void virtualizeWorldgenHasChunk(int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
        if (!usesVanillaRegionWindow()) {
            return;
        }

        GenerationWindow window = window();
        GenerationWindow.ChunkLookup lookup = window.resolveChunk(chunkX, chunkZ, ChunkStatus.EMPTY, false);
        if (lookup.kind() != GenerationWindow.ChunkLookup.Kind.VANILLA) {
            cir.setReturnValue(window.hasChunk(chunkX, chunkZ));
        }
    }

    @ModifyVariable(method = "getFluidState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenGetFluidStatePos(BlockPos pos) {
        return usesVanillaRegionWindow() ? window().canonicalReadPos(pos) : pos;
    }

    @ModifyVariable(method = "getBlockEntity", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenGetBlockEntityPos(BlockPos pos) {
        return usesVanillaRegionWindow() ? window().canonicalReadPos(pos) : pos;
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void skipUnavailableCanonicalBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (!usesVanillaRegionWindow()) {
            return;
        }

        GenerationWindow window = window();
        BlockPos wrapped = window.canonicalReadPos(pos);
        if (!window.physicalCacheContains(wrapped)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "ensureCanWrite", at = @At("HEAD"), cancellable = true)
    private void allowCanonicalWorldgenWrite(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!usesVanillaRegionWindow()) {
            return;
        }

        GenerationWindow window = window();
        BlockPos wrapped = window.canonicalReadPos(pos);
        if (!window.withinWriteRadius(wrapped)) {
            window.logWriteDecision(window.classifyWrite(pos));
            return;
        }

        if (!window.canWriteCanonical(wrapped)) {
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(true);
    }

    @ModifyVariable(method = "ensureCanWrite", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenEnsureCanWritePos(BlockPos pos) {
        return usesVanillaRegionWindow() ? window().canonicalReadPos(pos) : pos;
    }

    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void setCanonicalWorldgenBlock(
            BlockPos pos,
            BlockState blockState,
            int updateFlags,
            int updateLimit,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!usesVanillaRegionWindow()) {
            return;
        }

        GenerationWindow window = window();
        GenerationWindow.WriteDecision decision = window.classifyWrite(pos);
        if (decision.kind() == GenerationWindow.WriteDecision.Kind.CANONICAL) {
            return;
        }

        if (decision.kind() != GenerationWindow.WriteDecision.Kind.DENIED_BY_RADIUS) {
            window.logWriteDecision(decision);
        }
        WorldGenRegion region = (WorldGenRegion) (Object) this;
        if (!region.ensureCanWrite(decision.canonicalPos())) {
            cir.setReturnValue(false);
            return;
        }

        if (decision.kind() == GenerationWindow.WriteDecision.Kind.WRAPPED_UNOBSERVED) {
            WorldGenSpillover.enqueue(this.level, decision.canonicalPos(), null, blockState, updateFlags);
            cir.setReturnValue(true);
            return;
        }

        BlockState expectedState = region.getBlockState(decision.canonicalPos());
        boolean placed = region.setBlock(decision.canonicalPos(), blockState, updateFlags, updateLimit);
        if (placed) {
            WorldGenSpillover.enqueue(this.level, decision.canonicalPos(), expectedState, blockState, updateFlags);
        }
        cir.setReturnValue(placed);
    }

    @ModifyVariable(method = "setBlock", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenSetBlockPos(BlockPos pos) {
        return usesVanillaRegionWindow() ? window().canonicalReadPos(pos) : pos;
    }

    @ModifyVariable(method = "markPosForPostprocessing", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeWorldgenPostProcessingPos(BlockPos pos) {
        return usesVanillaRegionWindow() ? window().canonicalReadPos(pos) : pos;
    }

    private boolean usesVanillaRegionWindow() {
        return ((Object) this).getClass() == WorldGenRegion.class;
    }

    private GenerationWindow window() {
        return GenerationWindow.forRegion(this.level, this.center, this.cache, this.generatingStep);
    }
}
