package globe.world.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import globe.world.client.GlobeCurvatureShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {
    @Inject(
            method = "submitArmWithItem",
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
            ArmedEntityRenderState state,
            ItemStackRenderState itemState,
            ItemStack itemStack,
            HumanoidArm arm,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
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
