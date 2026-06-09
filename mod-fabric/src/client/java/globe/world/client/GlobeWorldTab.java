package globe.world.client;

import globe.world.config.GlobeSettings;
import globe.world.config.GlobeSettingsHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

public class GlobeWorldTab extends GridLayoutTab {
    private static final Component TITLE = Component.literal("Globe World");
    private static final int CONTENT_VERTICAL_PADDING = 8;

    private final ScrollableLayout scrollableLayout;

    public GlobeWorldTab(CreateWorldScreen screen) {
        super(TITLE);

        GlobeSettings initialSettings = ((GlobeSettingsHolder) (Object) screen.getUiState().getSettings())
                .globeWorld$getGlobeSettings();
        setSettings(screen, initialSettings);

        GlobeWorldSettingsControls controls = GlobeWorldSettingsControls.createWorld(
                GlobeWorldCreateState::get,
                settings -> setSettings(screen, settings)
        );
        FrameLayout paddedControls = new FrameLayout();
        paddedControls.addChild(controls, settings -> settings.paddingVertical(CONTENT_VERTICAL_PADDING));
        this.scrollableLayout = new ScrollableLayout(Minecraft.getInstance(), paddedControls, 1);
        controls.setLayoutChangedCallback(this.scrollableLayout::arrangeElements);
        this.layout.addChild(this.scrollableLayout, 0, 0);
    }

    @Override
    public void doLayout(ScreenRectangle screenRectangle) {
        this.scrollableLayout.setMaxHeight(screenRectangle.height());
        this.scrollableLayout.setMinHeight(screenRectangle.height());
        this.layout.arrangeElements();
        FrameLayout.alignInRectangle(this.layout, screenRectangle, 0.5F, 0.16666667F);
    }

    private static void setSettings(CreateWorldScreen screen, GlobeSettings settings) {
        GlobeSettings sanitized = settings == null ? GlobeSettings.DEFAULT : settings;
        GlobeWorldCreateState.set(sanitized);
        ((GlobeSettingsHolder) (Object) screen.getUiState().getSettings()).globeWorld$setGlobeSettings(sanitized);
    }
}
