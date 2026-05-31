package globe.world.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import globe.world.client.GlobeCurvatureShader;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    @Inject(
            method = "submit("
                    + "Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;"
                    + ")V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;submitMultipleFromCount("
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                            + "I"
                            + "Lnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;"
                            + "Lnet/minecraft/util/RandomSource;"
                            + "Lnet/minecraft/world/phys/AABB;"
                            + ")V"
            )
    )
    private void globeWorld$applyCurvatureToDroppedItem(
            ItemEntityRenderState state,
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
