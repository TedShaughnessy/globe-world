package globe.world.client.mixin;

import globe.world.util.DimensionTiling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(CloudRenderer.class)
public class CloudRendererMixin {
    @Unique
    private static final int globeWorld$CLOUD_PARALLAX_REFERENCE_CHUNKS = 64;
    @Unique
    private static final double globeWorld$MAX_CLOUD_PARALLAX_SCALE = 32.0D;

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Vec3 globeWorld$scaleCloudCameraMotion(Vec3 cameraPosition) {
        double scale = globeWorld$cloudParallaxScale();
        if (scale <= 1.0D) {
            return cameraPosition;
        }

        return new Vec3(cameraPosition.x * scale, cameraPosition.y, cameraPosition.z * scale);
    }

    @Unique
    private static double globeWorld$cloudParallaxScale() {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            return 1.0D;
        }

        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (!tiling.enabled()) {
            return 1.0D;
        }

        double scale = (double) globeWorld$CLOUD_PARALLAX_REFERENCE_CHUNKS / tiling.tileSizeChunks();
        return Math.clamp(scale, 1.0D, globeWorld$MAX_CLOUD_PARALLAX_SCALE);
    }
}
