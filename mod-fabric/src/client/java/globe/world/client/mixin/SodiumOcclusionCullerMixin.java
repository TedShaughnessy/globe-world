package globe.world.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.client.GlobeCurvatureShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller", remap = false)
public class SodiumOcclusionCullerMixin {
    @WrapOperation(
            method = "isWithinRenderDistance",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;abs(F)F"),
            require = 0
    )
    private static float globeWorld$expandVerticalRenderDistance(
            float value,
            Operation<Float> original,
            @Local(argsOnly = true) float horizontalDistance
    ) {
        float absoluteValue = original.call(value);
        float verticalDistance = GlobeCurvatureShader.sodiumVerticalRenderDistanceBlocks(horizontalDistance);
        if (verticalDistance == horizontalDistance || absoluteValue >= verticalDistance) {
            return absoluteValue;
        }

        return Math.min(absoluteValue, Math.nextDown(horizontalDistance));
    }
}
