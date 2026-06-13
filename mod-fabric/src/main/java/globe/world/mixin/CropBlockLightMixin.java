package globe.world.mixin;

import globe.world.topology.TopologyContexts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CropBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CropBlock.class)
public class CropBlockLightMixin {
    @Inject(method = "hasSufficientLight", at = @At("HEAD"), cancellable = true)
    private static void useCanonicalCropLight(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof Level concreteLevel && !concreteLevel.isClientSide()) {
            BlockPos canonicalPos = TopologyContexts.forLevel(concreteLevel).canonicalBlock(pos);
            cir.setReturnValue(level.getRawBrightness(canonicalPos, 0) >= 8);
        }
    }
}
