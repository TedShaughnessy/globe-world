package globe.world.client.mixin;

import globe.world.client.GlobeWorldSettingsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
    @Shadow
    @Final
    private boolean inWorld;

    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void globeWorld$addSettingsButton(CallbackInfo ci) {
        if (!this.inWorld) {
            return;
        }

        this.addRenderableWidget(Button.builder(
                        Component.literal("Globe World"),
                        button -> this.minecraft.setScreen(new GlobeWorldSettingsScreen((Screen) (Object) this))
                )
                .bounds(this.width - 112, 8, 104, 20)
                .build());
    }
}
