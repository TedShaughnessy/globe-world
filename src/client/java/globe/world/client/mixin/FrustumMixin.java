package globe.world.client.mixin;

import globe.world.client.GlobeCurvatureShader;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Frustum.class)
public abstract class FrustumMixin {
    @Shadow
    private double camX;
    @Shadow
    private double camZ;

    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void globeWorld$includeCurvedBounds(AABB bb, CallbackInfoReturnable<Boolean> cir) {
        double drop = globeWorld$maxCurvatureDrop(bb);
        if (drop <= 0.0D) {
            return;
        }

        int intersectionResult = this.globeWorld$cubeInFrustum(
                bb.minX,
                bb.minY - drop,
                bb.minZ,
                bb.maxX,
                bb.maxY,
                bb.maxZ
        );
        cir.setReturnValue(intersectionResult == -2 || intersectionResult == -1);
    }

    @Invoker("cubeInFrustum")
    protected abstract int globeWorld$cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);

    private double globeWorld$maxCurvatureDrop(AABB bb) {
        double minX = bb.minX - this.camX;
        double maxX = bb.maxX - this.camX;
        double minZ = bb.minZ - this.camZ;
        double maxZ = bb.maxZ - this.camZ;
        double maxDistanceSqr = Math.max(
                Math.max(minX * minX + minZ * minZ, minX * minX + maxZ * maxZ),
                Math.max(maxX * maxX + minZ * minZ, maxX * maxX + maxZ * maxZ)
        );
        return GlobeCurvatureShader.curvatureDrop(maxDistanceSqr);
    }
}
