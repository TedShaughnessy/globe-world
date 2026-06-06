package globe.world.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import globe.world.client.GlobeCurvatureShader;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TheEndPortalRenderer.class)
public class TheEndPortalRendererMixin {
    @Inject(
            method = "submit("
                    + "Lnet/minecraft/client/renderer/blockentity/state/EndPortalRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;"
                    + ")V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lcom/mojang/math/Transformation;)V"
            )
    )
    private void globeWorld$applyCurvatureToEndPortal(
            EndPortalRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera,
            CallbackInfo ci
    ) {
        BlockPos pos = state.blockPos;
        double relativeX = pos.getX() + 0.5D - camera.pos.x;
        double relativeZ = pos.getZ() + 0.5D - camera.pos.z;
        double drop = GlobeCurvatureShader.curvatureDrop(relativeX * relativeX + relativeZ * relativeZ);
        if (drop > 0.0D) {
            poseStack.translate(0.0D, -drop, 0.0D);
        }
    }
}
