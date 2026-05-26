package globe.world.mixin;

import globe.world.util.PeriodicNoiseUtil;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$ShiftA")
public abstract class DensityFunctionsShiftAMixin {
    @Shadow
    public abstract DensityFunction.NoiseHolder offsetNoise();

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void samplePeriodicShiftA(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
        DensityFunction.NoiseHolder noise = this.offsetNoise();
        double value = PeriodicNoiseUtil.sampleNoiseHolderXZ(
                context.blockX(),
                context.blockZ(),
                0.25,
                0.0,
                noise
        ) * 4.0;
        cir.setReturnValue(value);
    }
}
