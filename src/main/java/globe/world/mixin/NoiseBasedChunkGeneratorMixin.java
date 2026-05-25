package globe.world.mixin;

import globe.world.util.CoordUtil;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorMixin {
    @ModifyArg(
            method = "applyCarvers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"
            ),
            index = 1
    )
    private int wrapCarverSeedX(int sourceX) {
        return CoordUtil.wrapChunk(sourceX);
    }

    @ModifyArg(
            method = "applyCarvers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"
            ),
            index = 2
    )
    private int wrapCarverSeedZ(int sourceZ) {
        return CoordUtil.wrapChunk(sourceZ);
    }
}
