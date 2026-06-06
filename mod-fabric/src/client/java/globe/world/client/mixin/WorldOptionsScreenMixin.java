package globe.world.client.mixin;

import globe.world.client.GlobeWorldSettingsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
    private static final Component GLOBE_WORLD_SETTINGS_TOOLTIP = Component.literal(
            "Open Globe World wrapping, curvature, Nether, and day/night settings."
    );

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
        )
                .tooltip(Tooltip.create(GLOBE_WORLD_SETTINGS_TOOLTIP))
                .build());
        return addedRestrictionsButton;
    }
}
