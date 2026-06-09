package globe.world.client.mixin;

import globe.world.client.GlobeWorldTab;
import globe.world.client.GlobeWorldCreateState;
import globe.world.config.GlobeSettingsHolder;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Optional;

@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {
    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;addTabs([Lnet/minecraft/client/gui/components/tabs/Tab;)Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;"
            )
    )
    private TabNavigationBar.Builder globeWorld$addGlobeWorldTab(TabNavigationBar.Builder builder, Tab[] tabs) {
        Tab[] tabsWithGlobeWorld = Arrays.copyOf(tabs, tabs.length + 1);
        tabsWithGlobeWorld[tabs.length] = new GlobeWorldTab((CreateWorldScreen) (Object) this);
        return builder.addTabs(tabsWithGlobeWorld);
    }

    @Inject(method = "createWorldAndCleanup", at = @At("HEAD"))
    private void globeWorld$saveGlobeSettings(
            LayeredRegistryAccess<RegistryLayer> registries,
            LevelDataAndDimensions.WorldDataAndGenSettings dataAndGenSettings,
            Optional<GameRules> gameRules,
        CallbackInfo ci) {
        ((GlobeSettingsHolder) (Object) dataAndGenSettings.genSettings())
                .globeWorld$setGlobeSettings(GlobeWorldCreateState.get());
    }
}
