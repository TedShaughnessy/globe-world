package globe.world.mixin;

import globe.world.util.CoordUtil;
import globe.world.util.PeriodicNoiseUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SurfaceSystem.class)
public class SurfaceSystemMixin {
    @Shadow
    @Final
    private PositionalRandomFactory noiseRandom;

    @Shadow
    @Final
    private NormalNoise surfaceNoise;

    @Shadow
    @Final
    private NormalNoise surfaceSecondaryNoise;

    @Shadow
    @Final
    private NormalNoise clayBandsOffsetNoise;

    @Shadow
    @Final
    private BlockState[] clayBands;

    @Inject(method = "getSurfaceDepth", at = @At("HEAD"), cancellable = true)
    private void getPeriodicSurfaceDepth(int blockX, int blockZ, CallbackInfoReturnable<Integer> cir) {
        double noiseValue = sample(blockX, blockZ, 1.0, this.surfaceNoise);
        RandomSource random = this.noiseRandom.at(CoordUtil.wrapBlock(blockX), 0, CoordUtil.wrapBlock(blockZ));
        cir.setReturnValue((int) (noiseValue * 2.75 + 3.0 + random.nextDouble() * 0.25));
    }

    @Inject(method = "getSurfaceSecondary", at = @At("HEAD"), cancellable = true)
    private void getPeriodicSurfaceSecondary(int blockX, int blockZ, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(sample(blockX, blockZ, 1.0, this.surfaceSecondaryNoise));
    }

    @Inject(method = "getBand", at = @At("HEAD"), cancellable = true)
    private void getPeriodicClayBand(int worldX, int y, int worldZ, CallbackInfoReturnable<BlockState> cir) {
        int offset = (int) Math.round(sample(worldX, worldZ, 1.0, this.clayBandsOffsetNoise) * 4.0);
        cir.setReturnValue(this.clayBands[Math.floorMod(y + offset, this.clayBands.length)]);
    }

    @Redirect(
            method = "erodedBadlandsExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 0
            )
    )
    private double samplePeriodicBadlandsSurface(
            NormalNoise noise,
            double x,
            double y,
            double z,
            BlockColumn column,
            int blockX,
            int blockZ,
            int height,
            LevelHeightAccessor protoChunk) {
        return sample(blockX, blockZ, 1.0, noise);
    }

    @Redirect(
            method = "erodedBadlandsExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 1
            )
    )
    private double samplePeriodicBadlandsPillars(
            NormalNoise noise,
            double x,
            double y,
            double z,
            BlockColumn column,
            int blockX,
            int blockZ,
            int height,
            LevelHeightAccessor protoChunk) {
        return sample(blockX, blockZ, 0.2, noise);
    }

    @Redirect(
            method = "erodedBadlandsExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 2
            )
    )
    private double samplePeriodicBadlandsRoof(
            NormalNoise noise,
            double x,
            double y,
            double z,
            BlockColumn column,
            int blockX,
            int blockZ,
            int height,
            LevelHeightAccessor protoChunk) {
        return sample(blockX, blockZ, 0.75, noise);
    }

    @Redirect(
            method = "frozenOceanExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 0
            )
    )
    private double samplePeriodicIcebergSurface(
            NormalNoise noise,
            double x,
            double y,
            double z,
            int minSurfaceLevel,
            Biome surfaceBiome,
            BlockColumn column,
            BlockPos.MutableBlockPos blockPos,
            int blockX,
            int blockZ,
            int height) {
        return sample(blockX, blockZ, 1.0, noise);
    }

    @Redirect(
            method = "frozenOceanExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 1
            )
    )
    private double samplePeriodicIcebergPillars(
            NormalNoise noise,
            double x,
            double y,
            double z,
            int minSurfaceLevel,
            Biome surfaceBiome,
            BlockColumn column,
            BlockPos.MutableBlockPos blockPos,
            int blockX,
            int blockZ,
            int height) {
        return sample(blockX, blockZ, 1.28, noise);
    }

    @Redirect(
            method = "frozenOceanExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 2
            )
    )
    private double samplePeriodicIcebergRoof(
            NormalNoise noise,
            double x,
            double y,
            double z,
            int minSurfaceLevel,
            Biome surfaceBiome,
            BlockColumn column,
            BlockPos.MutableBlockPos blockPos,
            int blockX,
            int blockZ,
            int height) {
        return sample(blockX, blockZ, 1.17, noise);
    }

    @Redirect(
            method = "frozenOceanExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;at(III)Lnet/minecraft/util/RandomSource;"
            )
    )
    private RandomSource samplePeriodicIcebergRandom(
            PositionalRandomFactory randomFactory,
            int x,
            int y,
            int z) {
        return randomFactory.at(CoordUtil.wrapBlock(x), y, CoordUtil.wrapBlock(z));
    }

    private static double sample(int blockX, int blockZ, double scale, NormalNoise noise) {
        return PeriodicNoiseUtil.sampleNormalNoiseXZ(blockX, blockZ, scale, 0.0, noise);
    }
}
