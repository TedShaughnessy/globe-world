package globe.world.client;

import globe.world.config.DayNightCycleMode;
import globe.world.config.TilingMode;
import globe.world.config.TilingSettings;
import globe.world.config.TilingSettingsHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.CommonLayouts;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

public class GlobeWorldTab extends GridLayoutTab {
    private static final Component TITLE = Component.literal("Globe World");
    private static final int CONTROL_WIDTH = 310;

    public GlobeWorldTab(CreateWorldScreen screen) {
        super(TITLE);

        TilingSettings initialSettings = ((TilingSettingsHolder) (Object) screen.getUiState().getSettings())
                .globeWorld$getTilingSettings();
        setSettings(screen, initialSettings);
        Runnable[] refreshControlStates = new Runnable[1];

        EditBox tileSizeField = new EditBox(
                Minecraft.getInstance().font,
                60,
                20,
                Component.literal("Globe World Size")
        );
        tileSizeField.setValue(Integer.toString(initialSettings.tileSize()));
        tileSizeField.setMaxLength(6);

        GlobeCurvatureSlider curvatureSlider = new GlobeCurvatureSlider(
                0,
                0,
                CONTROL_WIDTH,
                20,
                initialSettings.curvaturePercent(),
                percent -> setSettings(screen, GlobeWorldCreateState.get().withCurvaturePercent(percent))
        );

        CycleButton<Boolean> enabledButton = CycleButton.onOffBuilder(initialSettings.enabled())
                .create(
                        0,
                        0,
                        CONTROL_WIDTH,
                        20,
                        Component.literal("Globe World Enabled"),
                        (button, enabled) -> {
                            TilingSettings updated = enabled
                                    ? GlobeWorldCreateState.get().withMode(TilingMode.SQUARE)
                                    : GlobeWorldCreateState.get().withMode(TilingMode.DISABLED);
                            setSettings(screen, updated);
                            refreshControlStates[0].run();
                        }
                );

        CycleButton<Boolean> netherEnabledButton = CycleButton.onOffBuilder(initialSettings.netherEnabled())
                .create(
                        0,
                        0,
                        CONTROL_WIDTH,
                        20,
                        Component.literal("Nether Globe Enabled"),
                        (button, enabled) -> {
                            TilingSettings updated = enabled
                                    ? GlobeWorldCreateState.get().withNetherMode(TilingMode.SQUARE)
                                    : GlobeWorldCreateState.get().withNetherMode(TilingMode.DISABLED);
                            setSettings(screen, updated);
                            refreshControlStates[0].run();
                        }
                );

        CycleButton<Boolean> netherOneEighthButton = CycleButton.onOffBuilder(initialSettings.netherOneEighthOverworldSize())
                .create(
                        0,
                        0,
                        CONTROL_WIDTH,
                        20,
                        Component.literal("Nether 1/8 Overworld Size"),
                        (button, enabled) -> setSettings(
                                screen,
                                GlobeWorldCreateState.get().withNetherOneEighthOverworldSize(enabled)
                        )
                );

        CycleButton<DayNightCycleMode> dayNightCycleButton = CycleButton.<DayNightCycleMode>builder(
                        mode -> Component.literal(mode.displayName()),
                        initialSettings.dayNightCycleMode()
                )
                .withValues(DayNightCycleMode.values())
                .create(
                        0,
                        0,
                        CONTROL_WIDTH,
                        20,
                        Component.literal("Day/Night Cycle"),
                        (button, mode) -> setSettings(screen, GlobeWorldCreateState.get().withDayNightCycleMode(mode))
                );

        tileSizeField.setResponder(text -> {
            try {
                int parsedSize = Math.max(1, Integer.parseInt(text));
                setSettings(screen, GlobeWorldCreateState.get().withTileSize(parsedSize));
                refreshControlStates[0].run();
            } catch (NumberFormatException ignored) {
            }
        });

        refreshControlStates[0] = () -> {
            TilingSettings settings = GlobeWorldCreateState.get();
            boolean enabled = settings.enabled();
            setTileSizeFieldEnabled(tileSizeField, enabled);
            curvatureSlider.active = enabled;
            netherEnabledButton.active = enabled;
            netherOneEighthButton.active = enabled
                    && settings.netherEnabled()
                    && settings.supportsNetherOneEighthOverworldSize();
            dayNightCycleButton.active = enabled;
        };
        refreshControlStates[0].run();

        GridLayout.RowHelper helper = this.layout.rowSpacing(8).createRowHelper(1);
        helper.addChild(enabledButton);
        helper.addChild(
                CommonLayouts.labeledElement(
                        Minecraft.getInstance().font,
                        tileSizeField,
                        Component.literal("Globe World Size")
                )
        );
        helper.addChild(curvatureSlider);
        helper.addChild(netherEnabledButton);
        helper.addChild(netherOneEighthButton);
        helper.addChild(dayNightCycleButton);
    }

    private static void setTileSizeFieldEnabled(EditBox field, boolean enabled) {
        field.active = enabled;
        field.setEditable(enabled);
    }

    private static void setSettings(CreateWorldScreen screen, TilingSettings settings) {
        TilingSettings sanitized = settings.sanitized();
        GlobeWorldCreateState.set(sanitized);
        ((TilingSettingsHolder) (Object) screen.getUiState().getSettings()).globeWorld$setTilingSettings(sanitized);
    }
}
