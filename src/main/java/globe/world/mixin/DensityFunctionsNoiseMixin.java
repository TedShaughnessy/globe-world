package globe.world.mixin;

import globe.world.util.PeriodicNoiseUtil;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Noise")
public abstract class DensityFunctionsNoiseMixin {
    @Shadow
    public abstract DensityFunction.NoiseHolder noise();

    @Shadow
    public abstract double xzScale();

    @Shadow
    public abstract double yScale();

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void samplePeriodicNoise(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
        double y = context.blockY() * this.yScale();
        DensityFunction.NoiseHolder noise = this.noise();
        double value = PeriodicNoiseUtil.sampleNoiseHolderXZ(
                context.blockX(),
                context.blockZ(),
                this.xzScale(),
                y,
                noise
        );
        cir.setReturnValue(value);
    }
}
