package globe.world.client.mixin;

import globe.world.client.GlobeWorldSettingsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.WorldOptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldOptionsScreen.class)
public class WorldOptionsScreenMixin {
    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;",
                    ordinal = 2
            )
    )
    private LayoutElement globeWorld$addSettingsButton(GridLayout.RowHelper helper, LayoutElement restrictionsButton) {
        LayoutElement addedRestrictionsButton = helper.addChild(restrictionsButton);
        helper.addChild(Button.builder(
                Component.literal("Globe World"),
                button -> net.minecraft.client.Minecraft.getInstance().setScreen(new GlobeWorldSettingsScreen((Screen) (Object) this))
        ).build());
        return addedRestrictionsButton;
    }
}
