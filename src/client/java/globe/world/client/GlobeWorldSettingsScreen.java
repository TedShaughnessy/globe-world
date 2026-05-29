package globe.world.client;

import globe.world.config.GlobeConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class GlobeWorldSettingsScreen extends Screen {
    private static final Component TITLE = Component.literal("Globe World Settings");
    private static final int CONTENT_VERTICAL_PADDING = 8;

    private final Screen parent;
    private HeaderAndFooterLayout layout;
    private ScrollableLayout scrollableLayout;

    public GlobeWorldSettingsScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.layout = new HeaderAndFooterLayout(this);
        this.layout.addToHeader(new StringWidget(TITLE, this.font), LayoutSettings::alignHorizontallyCenter);
        GlobeWorldSettingsControls controls = GlobeWorldSettingsControls.pauseMenu(
                GlobeConfig::tilingSettings,
                GlobeClientTilingSettings::setFromPauseMenu
        );
        FrameLayout paddedControls = new FrameLayout();
        paddedControls.addChild(controls, settings -> settings.paddingVertical(CONTENT_VERTICAL_PADDING));
        this.scrollableLayout = new ScrollableLayout(this.minecraft, paddedControls, this.layout.getContentHeight());
        controls.setLayoutChangedCallback(this.scrollableLayout::arrangeElements);
        this.layout.addToContents(this.scrollableLayout);
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(200).build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (this.layout != null) {
            int contentHeight = this.layout.getContentHeight();
            this.scrollableLayout.setMaxHeight(contentHeight);
            this.scrollableLayout.setMinHeight(contentHeight);
            this.layout.arrangeElements();
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
