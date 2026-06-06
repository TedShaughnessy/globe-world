package globe.world.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import globe.world.client.GlobeCurvatureShader;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownItemRenderer.class)
public class ThrownItemRendererMixin {
    @Inject(
            method = "submit("
                    + "Lnet/minecraft/client/renderer/entity/state/ThrownItemRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;"
                    + ")V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"
            )
    )
    private void globeWorld$applyCurvatureToThrownItem(
            ThrownItemRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera,
            CallbackInfo ci
    ) {
        double relativeX = state.x - camera.pos.x;
        double relativeZ = state.z - camera.pos.z;
        double drop = GlobeCurvatureShader.curvatureDrop(relativeX * relativeX + relativeZ * relativeZ);
        if (drop > 0.0D) {
            poseStack.translate(0.0D, -drop, 0.0D);
        }
    }
}
