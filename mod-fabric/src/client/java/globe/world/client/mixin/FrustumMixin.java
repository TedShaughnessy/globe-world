package globe.world.client.mixin;

import globe.world.client.GlobeCurvatureShader;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
        int intersectionResult = this.globeWorld$curvedCubeInFrustum(
                bb.minX,
                bb.minY,
                bb.minZ,
                bb.maxX,
                bb.maxY,
                bb.maxZ
        );
        if (intersectionResult == 0) {
            return;
        }

        cir.setReturnValue(intersectionResult == -2 || intersectionResult == -1);
    }

    @Inject(method = "cubeInFrustum(Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)I", at = @At("HEAD"), cancellable = true)
    private void globeWorld$includeCurvedBounds(BoundingBox bb, CallbackInfoReturnable<Integer> cir) {
        int intersectionResult = this.globeWorld$curvedCubeInFrustum(
                bb.minX(),
                bb.minY(),
                bb.minZ(),
                bb.maxX() + 1,
                bb.maxY() + 1,
                bb.maxZ() + 1
        );
        if (intersectionResult != 0) {
            cir.setReturnValue(intersectionResult);
        }
    }

    @Invoker("cubeInFrustum")
    protected abstract int globeWorld$cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);

    private int globeWorld$curvedCubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        int originalResult = this.globeWorld$cubeInFrustum(minX, minY, minZ, maxX, maxY, maxZ);
        double drop = globeWorld$maxCurvatureDrop(minX, minZ, maxX, maxZ);
        if (drop <= 0.0D || originalResult == -2) {
            return originalResult;
        }

        int curvedResult = this.globeWorld$cubeInFrustum(minX, minY - drop, minZ, maxX, maxY, maxZ);
        if (originalResult == -1 || curvedResult == -1 || curvedResult == -2) {
            return -1;
        }
        return originalResult;
    }

    private double globeWorld$maxCurvatureDrop(double minWorldX, double minWorldZ, double maxWorldX, double maxWorldZ) {
        double minX = minWorldX - this.camX;
        double maxX = maxWorldX - this.camX;
        double minZ = minWorldZ - this.camZ;
        double maxZ = maxWorldZ - this.camZ;
        double maxDistanceSqr = Math.max(
                Math.max(minX * minX + minZ * minZ, minX * minX + maxZ * maxZ),
                Math.max(maxX * maxX + minZ * minZ, maxX * maxX + maxZ * maxZ)
        );
        return GlobeCurvatureShader.curvatureDrop(maxDistanceSqr);
    }
}
