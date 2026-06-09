package globe.world.client.mixin;

import globe.world.config.GlobeSettings;
import globe.world.config.GlobeSettingsHolder;
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
public class WorldCreationContextMixin implements GlobeSettingsHolder {
    @Unique
    private GlobeSettings globeWorld$settings = GlobeSettings.DEFAULT;

    @Inject(method = "<init>(Lnet/minecraft/world/level/levelgen/WorldGenSettings;Lnet/minecraft/core/LayeredRegistryAccess;Lnet/minecraft/server/ReloadableServerResources;Lnet/minecraft/world/level/WorldDataConfiguration;)V", at = @At("TAIL"))
    private void globeWorld$copyGlobeSettings(
            WorldGenSettings worldGenSettings,
            LayeredRegistryAccess<RegistryLayer> loadedRegistries,
            ReloadableServerResources dataPackResources,
            WorldDataConfiguration dataConfiguration,
            CallbackInfo ci) {
        globeWorld$setGlobeSettings(((GlobeSettingsHolder) (Object) worldGenSettings).globeWorld$getGlobeSettings());
    }

    @Inject(method = "withSettings", at = @At("RETURN"))
    private void globeWorld$copyGlobeSettingsToUpdatedSettings(
            WorldOptions options,
            WorldDimensions dimensions,
            CallbackInfoReturnable<WorldCreationContext> cir) {
        globeWorld$copyGlobeSettingsTo(cir.getReturnValue());
    }

    @Inject(method = "withOptions", at = @At("RETURN"))
    private void globeWorld$copyGlobeSettingsToUpdatedOptions(
            WorldCreationContext.OptionsModifier modifier,
            CallbackInfoReturnable<WorldCreationContext> cir) {
        globeWorld$copyGlobeSettingsTo(cir.getReturnValue());
    }

    @Inject(method = "withDimensions", at = @At("RETURN"))
    private void globeWorld$copyGlobeSettingsToUpdatedDimensions(
            WorldCreationContext.DimensionsUpdater modifier,
            CallbackInfoReturnable<WorldCreationContext> cir) {
        globeWorld$copyGlobeSettingsTo(cir.getReturnValue());
    }

    @Override
    public GlobeSettings globeWorld$getGlobeSettings() {
        return globeWorld$settings;
    }

    @Override
    public void globeWorld$setGlobeSettings(GlobeSettings settings) {
        globeWorld$settings = settings == null ? GlobeSettings.DEFAULT : settings;
    }

    @Unique
    private void globeWorld$copyGlobeSettingsTo(WorldCreationContext context) {
        ((GlobeSettingsHolder) (Object) context).globeWorld$setGlobeSettings(globeWorld$settings);
    }
}
