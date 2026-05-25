package globe.world.mixin;

import globe.world.util.PeriodicNoiseUtil;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Shift")
public abstract class DensityFunctionsShiftMixin {
    @Shadow
    public abstract DensityFunction.NoiseHolder offsetNoise();

    @Inject(method = "compute(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D", at = @At("HEAD"), cancellable = true)
    private void samplePeriodicShift(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
        double y = context.blockY() * 0.25;
        DensityFunction.NoiseHolder noise = this.offsetNoise();
        double value = PeriodicNoiseUtil.samplePlane(
                context.blockX(),
                context.blockZ(),
                0.25,
                (x, z) -> noise.getValue(x, y, z) * 4.0
        );
        cir.setReturnValue(value);
    }
}
