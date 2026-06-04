package globe.world.mixin;

import globe.world.util.PeriodicNoiseUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlendedNoise.class)
public class BlendedNoiseMixin {
    @Shadow
    @Final
    private PerlinNoise minLimitNoise;

    @Shadow
    @Final
    private PerlinNoise maxLimitNoise;

    @Shadow
    @Final
    private PerlinNoise mainNoise;

    @Shadow
    @Final
    private double xzMultiplier;

    @Shadow
    @Final
    private double yMultiplier;

    @Shadow
    @Final
    private double xzFactor;

    @Shadow
    @Final
    private double yFactor;

    @Shadow
    @Final
    private double smearScaleMultiplier;

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void computePeriodicBlendedNoise(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
        double limitY = context.blockY() * this.yMultiplier;
        double mainY = limitY / this.yFactor;
        double limitSmear = this.yMultiplier * this.smearScaleMultiplier;
        double mainSmear = limitSmear / this.yFactor;
        double mainNoiseValue = 0.0;
        double pow = 1.0;

        for (int i = 0; i < 8; i++) {
            ImprovedNoise noise = this.mainNoise.getOctaveNoise(i);
            if (noise != null) {
                double octavePow = pow;
                double scale = this.xzMultiplier / this.xzFactor * octavePow;
                double y = PerlinNoise.wrap(mainY * octavePow);
                double yScalePow = mainSmear * octavePow;
                double yFudge = mainY * octavePow;
                mainNoiseValue += PeriodicNoiseUtil.sampleImprovedNoiseXZ(
                        context.blockX(),
                        context.blockZ(),
                        scale,
                        y,
                        yScalePow,
                        yFudge,
                        noise
                ) / octavePow;
            }

            pow /= 2.0;
        }

        double factor = (mainNoiseValue / 10.0 + 1.0) / 2.0;
        boolean isMax = factor >= 1.0;
        boolean isMin = factor <= 0.0;
        double blendMin = 0.0;
        double blendMax = 0.0;
        pow = 1.0;

        for (int i = 0; i < 16; i++) {
            double scale = this.xzMultiplier * pow;
            double y = PerlinNoise.wrap(limitY * pow);
            double yScalePow = limitSmear * pow;
            double yFudge = limitY * pow;
            if (!isMax) {
                ImprovedNoise minNoise = this.minLimitNoise.getOctaveNoise(i);
                if (minNoise != null) {
                    blendMin += PeriodicNoiseUtil.sampleImprovedNoiseXZ(
                            context.blockX(),
                            context.blockZ(),
                            scale,
                            y,
                            yScalePow,
                            yFudge,
                            minNoise
                    ) / pow;
                }
            }

            if (!isMin) {
                ImprovedNoise maxNoise = this.maxLimitNoise.getOctaveNoise(i);
                if (maxNoise != null) {
                    blendMax += PeriodicNoiseUtil.sampleImprovedNoiseXZ(
                            context.blockX(),
                            context.blockZ(),
                            scale,
                            y,
                            yScalePow,
                            yFudge,
                            maxNoise
                    ) / pow;
                }
            }

            pow /= 2.0;
        }

        cir.setReturnValue(Mth.clampedLerp(factor, blendMin / 512.0, blendMax / 512.0) / 128.0);
    }
}
