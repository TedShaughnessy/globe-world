package globe.world.mixin;

import globe.world.util.CoordUtil;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(StructurePlacement.class)
public class StructurePlacementMixin {
    @ModifyArg(
            method = "probabilityReducer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureWithSalt(JIII)V"
            ),
            index = 2
    )
    private static int wrapProbabilityReducerSeedX(int sourceX) {
        return CoordUtil.wrapChunk(sourceX);
    }

    @ModifyArg(
            method = "probabilityReducer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureWithSalt(JIII)V"
            ),
            index = 3
    )
    private static int wrapProbabilityReducerSeedZ(int sourceZ) {
        return CoordUtil.wrapChunk(sourceZ);
    }

    @ModifyArg(
            method = "legacyProbabilityReducerWithDouble",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"
            ),
            index = 1
    )
    private static int wrapLegacyProbabilitySeedX(int sourceX) {
        return CoordUtil.wrapChunk(sourceX);
    }

    @ModifyArg(
            method = "legacyProbabilityReducerWithDouble",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"
            ),
            index = 2
    )
    private static int wrapLegacyProbabilitySeedZ(int sourceZ) {
        return CoordUtil.wrapChunk(sourceZ);
    }

    @ModifyArg(
            method = "legacyArbitrarySaltProbabilityReducer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureWithSalt(JIII)V"
            ),
            index = 1
    )
    private static int wrapLegacyArbitrarySaltSeedX(int sourceX) {
        return CoordUtil.wrapChunk(sourceX);
    }

    @ModifyArg(
            method = "legacyArbitrarySaltProbabilityReducer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureWithSalt(JIII)V"
            ),
            index = 2
    )
    private static int wrapLegacyArbitrarySaltSeedZ(int sourceZ) {
        return CoordUtil.wrapChunk(sourceZ);
    }
}
