package globe.world.client.mixin;

import globe.world.client.GlobeCurvatureShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.tree.TraversableTree", remap = false)
public class SodiumTraversableTreeMixin {
    @Inject(method = "cylindricalDistanceTest(FFFF)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private static void globeWorld$useHorizontalDistanceWithExpandedVerticalDistance(
            float dx,
            float dy,
            float dz,
            float distanceLimit,
            CallbackInfoReturnable<Boolean> cir
    ) {
        float verticalDistance = GlobeCurvatureShader.sodiumVerticalRenderDistanceBlocks(distanceLimit);
        if (verticalDistance == distanceLimit) {
            return;
        }

        cir.setReturnValue(dx * dx + dz * dz < distanceLimit * distanceLimit && Math.abs(dy) < verticalDistance);
    }
}
