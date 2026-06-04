package globe.world.client.mixin;

import globe.world.client.GlobeCurvatureShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.client.renderer.SectionOcclusionGraph$GraphStorage")
public class SectionOcclusionGraphStorageMixin {
    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Octree;<init>(Lnet/minecraft/core/SectionPos;III)V"
            ),
            index = 1
    )
    private int globeWorld$expandCurvedTerrainOctreeHeight(int renderDistance) {
        return GlobeCurvatureShader.curvatureRadius() > 0.0D ? Math.max(renderDistance, 32) : renderDistance;
    }
}
