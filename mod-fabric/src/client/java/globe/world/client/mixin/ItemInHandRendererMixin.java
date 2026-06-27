package globe.world.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import globe.world.client.render.GlobeHeldMapRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Inject(
            method = "renderHandsWithItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;getFeatureRenderDispatcher()"
                            + "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;"
            )
    )
    private void globeWorld$renderAtlasProjectorMapsAfterHands(
            final float frameInterp,
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final LocalPlayer player,
            final int lightCoords,
            final CallbackInfo ci) {
        if (GlobeHeldMapRenderer.isAtlasProjector(this.mainHandItem)) {
            GlobeHeldMapRenderer.renderAboveAtlas(
                    poseStack,
                    submitNodeCollector,
                    lightCoords,
                    player.getMainArm(),
                    player.getYRot(frameInterp));
        }
        if (GlobeHeldMapRenderer.isAtlasProjector(this.offHandItem)) {
            GlobeHeldMapRenderer.renderAboveAtlas(
                    poseStack,
                    submitNodeCollector,
                    lightCoords,
                    player.getMainArm().getOpposite(),
                    player.getYRot(frameInterp));
        }
    }
}
