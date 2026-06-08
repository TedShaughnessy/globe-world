package globe.world.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import globe.world.client.GlobeCurvatureShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.FoxHeldItemLayer;
import net.minecraft.client.renderer.entity.state.FoxRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoxHeldItemLayer.class)
public class FoxHeldItemLayerMixin {
    @Inject(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit("
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                            + "III"
                            + ")V"
            )
    )
    private void globeWorld$applyCurvatureToHeldItem(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            FoxRenderState state,
            float yRot,
            float xRot,
            CallbackInfo ci
    ) {
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        double relativeX = state.x - cameraPos.x;
        double relativeZ = state.z - cameraPos.z;
        double drop = GlobeCurvatureShader.curvatureDrop(relativeX * relativeX + relativeZ * relativeZ);
        if (drop > 0.0D) {
            poseStack.last().pose().translateLocal(0.0F, (float) -drop, 0.0F);
        }
    }
}
