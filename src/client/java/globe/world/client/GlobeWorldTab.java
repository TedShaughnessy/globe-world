package globe.world.client;

import globe.world.config.TilingSettings;
import globe.world.config.TilingSettingsHolder;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

public class GlobeWorldTab extends GridLayoutTab {
    private static final Component TITLE = Component.literal("Globe World");

    public GlobeWorldTab(CreateWorldScreen screen) {
        super(TITLE);

        TilingSettings initialSettings = ((TilingSettingsHolder) (Object) screen.getUiState().getSettings())
                .globeWorld$getTilingSettings();
        setSettings(screen, initialSettings);

        this.layout.addChild(GlobeWorldSettingsControls.createWorld(
                GlobeWorldCreateState::get,
                settings -> setSettings(screen, settings)
        ), 0, 0);
    }

    private static void setSettings(CreateWorldScreen screen, TilingSettings settings) {
        TilingSettings sanitized = settings.sanitized();
        GlobeWorldCreateState.set(sanitized);
        ((TilingSettingsHolder) (Object) screen.getUiState().getSettings()).globeWorld$setTilingSettings(sanitized);
    }
}
