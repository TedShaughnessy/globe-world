package globe.world.client.mixin;

import globe.world.client.GlobeWorldCreateState;
import globe.world.config.TilingSettingsHolder;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {
    @Inject(method = "createWorldAndCleanup", at = @At("HEAD"))
    private void globeWorld$saveTilingSettings(
            LayeredRegistryAccess<RegistryLayer> registries,
            LevelDataAndDimensions.WorldDataAndGenSettings dataAndGenSettings,
            Optional<GameRules> gameRules,
            CallbackInfo ci) {
        ((TilingSettingsHolder) (Object) dataAndGenSettings.genSettings())
                .globeWorld$setTilingSettings(GlobeWorldCreateState.get());
    }
}
