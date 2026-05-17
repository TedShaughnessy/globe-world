package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelSetBlockBroadcastMixin {
    @Unique
    private BlockState globeWorld$setBlockOldState;

    @Unique
    private boolean globeWorld$sentBlockUpdate;

    @Shadow
    public abstract boolean isClientSide();

    @Shadow
    public abstract void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags);

    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD")
    )
    private void resetSetBlockBroadcastTracking(
            BlockPos pos,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        globeWorld$setBlockOldState = null;
        globeWorld$sentBlockUpdate = false;
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
        globeWorld$setBlockOldState = oldState;
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
        globeWorld$sentBlockUpdate = true;
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
        if (isClientSide() || !cir.getReturnValue() || globeWorld$sentBlockUpdate || globeWorld$setBlockOldState == null) {
            return;
        }
        if ((flags & 2) == 0) {
            return;
        }
        sendBlockUpdated(pos, globeWorld$setBlockOldState, state, flags);
    }
}
