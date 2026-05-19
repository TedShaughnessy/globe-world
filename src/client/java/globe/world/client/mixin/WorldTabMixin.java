package globe.world.client.mixin;

import globe.world.client.GlobeWorldCreateState;
import globe.world.config.TilingMode;
import globe.world.config.TilingSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.CommonLayouts;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class WorldTabMixin extends GridLayoutTab {
    private WorldTabMixin() {
        super(Component.empty());
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void globeWorld$addTilingOptions(CreateWorldScreen screen, CallbackInfo ci) {
        TilingSettings initialSettings = TilingSettings.DEFAULT;
        GlobeWorldCreateState.set(initialSettings);

        EditBox tileSizeField = new EditBox(
                Minecraft.getInstance().font,
                60,
                20,
                Component.literal("Globe World Size")
        );
        tileSizeField.setValue(Integer.toString(initialSettings.tileSize()));
        tileSizeField.setMaxLength(6);

        CycleButton<TilingMode> modeButton = CycleButton.<TilingMode>builder(
                        mode -> Component.literal(mode.displayName()),
                        initialSettings.mode()
                )
                .withValues(TilingMode.values())
                .create(
                        0,
                        0,
                        310,
                        20,
                        Component.literal("Globe World"),
                        (button, mode) -> {
                            TilingSettings updated = GlobeWorldCreateState.get().withMode(mode);
                            GlobeWorldCreateState.set(updated);
                            globeWorld$setTileSizeFieldEnabled(tileSizeField, updated.enabled());
                        }
                );

        tileSizeField.setResponder(text -> {
            try {
                int parsedSize = Math.max(1, Integer.parseInt(text));
                GlobeWorldCreateState.set(GlobeWorldCreateState.get().withTileSize(parsedSize));
            } catch (NumberFormatException ignored) {
            }
        });

        globeWorld$setTileSizeFieldEnabled(tileSizeField, initialSettings.enabled());

        GridLayout layout = this.layout;
        layout.addChild(modeButton, 3, 0, 1, 2);
        layout.addChild(
                CommonLayouts.labeledElement(
                        Minecraft.getInstance().font,
                        tileSizeField,
                        Component.literal("Globe World Size")
                ),
                4,
                0,
                1,
                2
        );
    }

    private static void globeWorld$setTileSizeFieldEnabled(EditBox field, boolean enabled) {
        field.active = enabled;
        field.setEditable(enabled);
    }
}
