package globe.world.mixin;

import globe.world.util.PeriodicNoiseUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.NoiseThresholdCountPlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseThresholdCountPlacement.class)
public class NoiseThresholdCountPlacementMixin {
    @Shadow
    @Final
    private double noiseLevel;

    @Shadow
    @Final
    private int belowNoise;

    @Shadow
    @Final
    private int aboveNoise;

    @Inject(method = "count", at = @At("HEAD"), cancellable = true)
    private void countPeriodicNoise(RandomSource random, BlockPos origin, CallbackInfoReturnable<Integer> cir) {
        double flowerNoise = PeriodicNoiseUtil.samplePlane(
                origin.getX(),
                origin.getZ(),
                1.0 / 200.0,
                (x, z) -> Biome.BIOME_INFO_NOISE.getValue(x, z, false)
        );
        cir.setReturnValue(flowerNoise < this.noiseLevel ? this.belowNoise : this.aboveNoise);
    }
}
