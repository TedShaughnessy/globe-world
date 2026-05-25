package globe.world.mixin;

import globe.world.util.PeriodicNoiseUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseBasedStateProvider;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedStateProvider.class)
public class NoiseBasedStateProviderMixin {
    @Shadow
    @Final
    protected NormalNoise noise;

    @Inject(method = "getNoiseValue", at = @At("HEAD"), cancellable = true)
    private void samplePeriodicStateProviderNoise(BlockPos pos, double scale, CallbackInfoReturnable<Double> cir) {
        double y = pos.getY() * scale;
        cir.setReturnValue(PeriodicNoiseUtil.samplePlane(
                pos.getX(),
                pos.getZ(),
                scale,
                (x, z) -> this.noise.getValue(x, y, z)
        ));
    }
}
