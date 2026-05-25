package globe.world.mixin;

import globe.world.util.PeriodicNoiseUtil;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$ShiftedNoise")
public abstract class DensityFunctionsShiftedNoiseMixin {
    @Shadow
    public abstract DensityFunction shiftX();

    @Shadow
    public abstract DensityFunction shiftY();

    @Shadow
    public abstract DensityFunction shiftZ();

    @Shadow
    public abstract double xzScale();

    @Shadow
    public abstract double yScale();

    @Shadow
    public abstract DensityFunction.NoiseHolder noise();

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void samplePeriodicShiftedNoise(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
        double y = context.blockY() * this.yScale() + this.shiftY().compute(context);
        double shiftX = this.shiftX().compute(context);
        double shiftZ = this.shiftZ().compute(context);
        DensityFunction.NoiseHolder noise = this.noise();
        double value = PeriodicNoiseUtil.samplePlane(
                context.blockX(),
                context.blockZ(),
                this.xzScale(),
                (x, z) -> noise.getValue(x + shiftX, y, z + shiftZ)
        );
        cir.setReturnValue(value);
    }
}
