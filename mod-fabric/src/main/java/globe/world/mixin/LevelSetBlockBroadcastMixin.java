package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.diagnostics.DiagnosticsChannel;
import globe.world.diagnostics.GlobeDiagnostics;
import globe.world.topology.TopologyContexts;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelSetBlockBroadcastMixin {
    @Unique
    private final ArrayDeque<GlobeWorldSetBlockFrame> globeWorld$setBlockFrames = new ArrayDeque<>();

    @Unique
    private static int globeWorld$blockMutationLogCount;

    @Shadow
    public abstract boolean isClientSide();

    @Shadow
    public abstract void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags);

    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

    @ModifyVariable(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private BlockPos canonicalizeServerSetBlockPos(BlockPos pos) {
        return isClientSide() ? pos : globeWorld$canonicalBlock(pos);
    }

    @ModifyVariable(method = "getBlockEntity", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeServerGetBlockEntityPos(BlockPos pos) {
        return isClientSide() ? pos : globeWorld$canonicalBlock(pos);
    }

    @ModifyVariable(method = "removeBlockEntity", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeServerRemoveBlockEntityPos(BlockPos pos) {
        return isClientSide() ? pos : globeWorld$canonicalBlock(pos);
    }

    @ModifyVariable(method = "blockEntityChanged", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeServerBlockEntityChangedPos(BlockPos pos) {
        return isClientSide() ? pos : globeWorld$canonicalBlock(pos);
    }

    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"),
        order = 900
    )
    private void resetSetBlockBroadcastTracking(
            BlockPos pos,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        GlobeWorldSetBlockFrame frame = new GlobeWorldSetBlockFrame();
        frame.originalPos = pos;
        frame.canonicalPos = globeWorld$canonicalBlock(pos);
        frame.targetState = state;
        frame.flags = flags;
        frame.caller = globeWorld$caller();
        globeWorld$setBlockFrames.push(frame);
    }

    @WrapOperation(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState captureOldState(
            LevelChunk chunk,
            BlockPos pos,
            BlockState state,
            int flags,
            Operation<BlockState> original) {
        BlockState oldState = original.call(chunk, pos, state, flags);
        GlobeWorldSetBlockFrame frame = globeWorld$currentSetBlockFrame();
        if (frame != null) {
            frame.oldState = oldState;
            frame.mutationPos = pos;
            frame.chunkPos = chunk.getPos();
            globeWorld$logInterestingBlockMutation(frame, state);
        }
        return oldState;
    }

    @WrapOperation(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;sendBlockUpdated(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V"
        )
    )
    private void markVanillaBlockUpdateSent(
            Level level,
            BlockPos pos,
            BlockState oldState,
            BlockState newState,
            int flags,
            Operation<Void> original) {
        GlobeWorldSetBlockFrame frame = globeWorld$currentSetBlockFrame();
        if (frame != null) {
            frame.sentBlockUpdate = true;
        }
        original.call(level, pos, oldState, newState, flags);
    }

    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("RETURN")
    )
    private void sendSkippedServerBlockUpdate(
            BlockPos pos,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        GlobeWorldSetBlockFrame frame = globeWorld$setBlockFrames.poll();
        if (frame == null || isClientSide() || !cir.getReturnValue() || frame.sentBlockUpdate || frame.oldState == null) {
            return;
        }
        if ((flags & 2) == 0) {
            return;
        }
        BlockState actualState = getBlockState(pos);
        if (actualState != frame.oldState) {
            sendBlockUpdated(pos, frame.oldState, actualState, flags);
        }
    }

    @Unique
    private GlobeWorldSetBlockFrame globeWorld$currentSetBlockFrame() {
        return globeWorld$setBlockFrames.peek();
    }

    @Unique
    private BlockPos globeWorld$canonicalBlock(BlockPos pos) {
        return TopologyContexts.forLevel((Level) (Object) this).canonicalBlock(pos);
    }

    @Unique
    private void globeWorld$logInterestingBlockMutation(GlobeWorldSetBlockFrame frame, BlockState newState) {
        if (isClientSide() || !globeWorld$isInterestingMutation(frame.oldState, newState)) {
            return;
        }
        if (globeWorld$blockMutationLogCount >= 200) {
            if (globeWorld$blockMutationLogCount == 200) {
                GlobeDiagnostics.warn(
                        DiagnosticsChannel.BLOCK_MUTATION,
                        "GW_BLOCK_MUTATION logging limit reached; suppressing further grass/dirt mutation logs");
                globeWorld$blockMutationLogCount++;
            }
            return;
        }
        globeWorld$blockMutationLogCount++;

        GlobeDiagnostics.warn(
                DiagnosticsChannel.BLOCK_MUTATION,
                "GW_BLOCK_MUTATION original={} canonical={} mutation={} chunk={} old={} new={} flags={} caller={}",
                frame.originalPos,
                frame.canonicalPos,
                frame.mutationPos,
                frame.chunkPos,
                frame.oldState,
                newState,
                frame.flags,
                frame.caller);
    }

    @Unique
    private static boolean globeWorld$isInterestingMutation(BlockState oldState, BlockState newState) {
        if (newState == null || !globeWorld$isGrassOrDirtLike(newState)) {
            return false;
        }
        if (oldState == null) {
            return true;
        }
        return oldState.isAir() || oldState.getFluidState().isSourceOfType(Fluids.WATER)
                || !oldState.is(newState.getBlock());
    }

    @Unique
    private static boolean globeWorld$isGrassOrDirtLike(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS);
    }

    @Unique
    private static String globeWorld$caller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className.startsWith("java.")
                    || className.startsWith("org.spongepowered.")
                    || className.startsWith("com.llamalad7.")
                    || className.equals(LevelSetBlockBroadcastMixin.class.getName())
                    || className.equals(Thread.class.getName())) {
                continue;
            }
            return className + "#" + element.getMethodName() + ":" + element.getLineNumber();
        }
        return "unknown";
    }

    @Unique
    private static class GlobeWorldSetBlockFrame {
        private BlockPos originalPos;
        private BlockPos canonicalPos;
        private BlockPos mutationPos;
        private BlockState targetState;
        private BlockState oldState;
        private net.minecraft.world.level.ChunkPos chunkPos;
        private int flags;
        private String caller;
        private boolean sentBlockUpdate;
    }
}
