package globe.world.client.mixin;

import globe.world.config.TilingSettings;
import globe.world.config.TilingSettingsHolder;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldCreationContext.class)
public class WorldCreationContextMixin implements TilingSettingsHolder {
    @Unique
    private TilingSettings globeWorld$tilingSettings = TilingSettings.DEFAULT;

    @Inject(method = "<init>(Lnet/minecraft/world/level/levelgen/WorldGenSettings;Lnet/minecraft/core/LayeredRegistryAccess;Lnet/minecraft/server/ReloadableServerResources;Lnet/minecraft/world/level/WorldDataConfiguration;)V", at = @At("TAIL"))
    private void globeWorld$copyTilingSettings(
            WorldGenSettings worldGenSettings,
            LayeredRegistryAccess<RegistryLayer> loadedRegistries,
            ReloadableServerResources dataPackResources,
            WorldDataConfiguration dataConfiguration,
            CallbackInfo ci) {
        globeWorld$setTilingSettings(((TilingSettingsHolder) (Object) worldGenSettings).globeWorld$getTilingSettings());
    }

    @Inject(method = "withSettings", at = @At("RETURN"))
    private void globeWorld$copyTilingSettingsToUpdatedSettings(
            WorldOptions options,
            WorldDimensions dimensions,
            CallbackInfoReturnable<WorldCreationContext> cir) {
        globeWorld$copyTilingSettingsTo(cir.getReturnValue());
    }

    @Inject(method = "withOptions", at = @At("RETURN"))
    private void globeWorld$copyTilingSettingsToUpdatedOptions(
            WorldCreationContext.OptionsModifier modifier,
            CallbackInfoReturnable<WorldCreationContext> cir) {
        globeWorld$copyTilingSettingsTo(cir.getReturnValue());
    }

    @Inject(method = "withDimensions", at = @At("RETURN"))
    private void globeWorld$copyTilingSettingsToUpdatedDimensions(
            WorldCreationContext.DimensionsUpdater modifier,
            CallbackInfoReturnable<WorldCreationContext> cir) {
        globeWorld$copyTilingSettingsTo(cir.getReturnValue());
    }

    @Override
    public TilingSettings globeWorld$getTilingSettings() {
        return globeWorld$tilingSettings;
    }

    @Override
    public void globeWorld$setTilingSettings(TilingSettings settings) {
        globeWorld$tilingSettings = settings.sanitized();
    }

    @Unique
    private void globeWorld$copyTilingSettingsTo(WorldCreationContext context) {
        ((TilingSettingsHolder) (Object) context).globeWorld$setTilingSettings(globeWorld$tilingSettings);
    }
}
