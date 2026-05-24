package globe.world.client.mixin;

import globe.world.client.GlobeCurvatureSlider;
import globe.world.config.GlobeConfig;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(OptionsScreen.class)
public class OptionsScreenMixin {
    @Shadow
    @Final
    private boolean inWorld;

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/layouts/LinearLayout;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;",
                    ordinal = 1
            )
    )
    private LayoutElement globeWorld$addCurvatureSliderAfterFov(LinearLayout layout, LayoutElement child) {
        LayoutElement addedChild = layout.addChild(child);
        if (this.inWorld && GlobeConfig.enabled()) {
            layout.addChild(new GlobeCurvatureSlider(
                    0,
                    0,
                    150,
                    20,
                    GlobeConfig.curvaturePercent(),
                    percent -> GlobeConfig.setTilingSettings(GlobeConfig.tilingSettings().withCurvaturePercent(percent))
            ));
        }
        return addedChild;
    }
}
