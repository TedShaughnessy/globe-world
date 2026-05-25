package globe.world.client.mixin;

import globe.world.client.GlobeCurvatureShader;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SectionOcclusionGraph.class)
public class SectionOcclusionGraphMixin {
    @Redirect(
            method = "getRelativeFrom",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;abs(I)I")
    )
    private int globeWorld$ignoreVerticalViewDistanceForCurvedTerrain(int value) {
        return GlobeCurvatureShader.curvatureRadius() > 0.0D ? 0 : Mth.abs(value);
    }
}
