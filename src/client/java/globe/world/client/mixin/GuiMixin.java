package globe.world.client.mixin;

import globe.world.client.GlobeDebugHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "extractDebugOverlay", at = @At("TAIL"))
    private void globeWorld$extractDebugOverlay(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        GlobeDebugHud.extractRenderState(graphics);
    }
}
