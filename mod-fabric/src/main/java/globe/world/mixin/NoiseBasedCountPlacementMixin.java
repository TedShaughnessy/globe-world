package globe.world.mixin;

import globe.world.util.PeriodicNoiseUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.NoiseBasedCountPlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedCountPlacement.class)
public class NoiseBasedCountPlacementMixin {
    @Shadow
    @Final
    private int noiseToCountRatio;

    @Shadow
    @Final
    private double noiseFactor;

    @Shadow
    @Final
    private double noiseOffset;

    @Inject(method = "count", at = @At("HEAD"), cancellable = true)
    private void countPeriodicNoise(RandomSource random, BlockPos origin, CallbackInfoReturnable<Integer> cir) {
        double flowerNoise = PeriodicNoiseUtil.samplePlane(
                origin.getX(),
                origin.getZ(),
                1.0 / this.noiseFactor,
                (x, z) -> Biome.BIOME_INFO_NOISE.getValue(x, z, false)
        );
        cir.setReturnValue((int) Math.ceil((flowerNoise + this.noiseOffset) * this.noiseToCountRatio));
    }
}
