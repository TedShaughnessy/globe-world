package globe.world.client.mixin;

import globe.world.client.GlobeDebugState;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    private static final int GLFW_KEY_Y = 89;

    @Shadow
    private void debugFeedbackComponent(Component component) {
    }

    @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
    private void globeWorld$handleDebugKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.key() == GLFW_KEY_Y && !event.hasControlDown() && !event.hasShiftDown()) {
            boolean enabled = GlobeDebugState.toggleDebugScreen();
            this.debugFeedbackComponent(Component.literal("Globe World debug visuals: " + (enabled ? "shown" : "hidden")));
            cir.setReturnValue(true);
        }
    }
}
