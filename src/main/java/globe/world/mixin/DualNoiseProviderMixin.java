package globe.world.mixin;

import globe.world.util.PeriodicNoiseUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.stateproviders.DualNoiseProvider;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DualNoiseProvider.class)
public class DualNoiseProviderMixin {
    @Shadow
    @Final
    private float slowScale;

    @Shadow
    @Final
    private NormalNoise slowNoise;

    @Inject(method = "getSlowNoiseValue", at = @At("HEAD"), cancellable = true)
    private void samplePeriodicSlowNoise(BlockPos pos, CallbackInfoReturnable<Double> cir) {
        double y = pos.getY() * this.slowScale;
        cir.setReturnValue(PeriodicNoiseUtil.sampleNormalNoiseXZ(
                pos.getX(),
                pos.getZ(),
                this.slowScale,
                y,
                this.slowNoise
        ));
    }
}
