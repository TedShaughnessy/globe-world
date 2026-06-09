package globe.world.client.mixin;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import globe.world.config.GlobeSettings;
import globe.world.config.TilingSettingsHolder;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldOpenFlows.class)
public class WorldOpenFlowsMixin {
    @Inject(method = "recreateWorldData", at = @At("RETURN"))
    private void globeWorld$copyTilingSettingsToRecreatedContext(
            LevelStorageAccess levelSourceAccess,
            CallbackInfoReturnable<Pair<LevelSettings, WorldCreationContext>> cir) {
        WorldCreationContext context = cir.getReturnValue().getSecond();
        DataResult<WorldGenSettings> worldGenSettings = LevelStorageSource.readExistingSavedData(
                levelSourceAccess,
                context.worldgenLoadContext(),
                WorldGenSettings.TYPE
        );
        worldGenSettings.result().ifPresent(settings -> {
            GlobeSettings globeSettings = ((TilingSettingsHolder) (Object) settings).globeWorld$getGlobeSettings();
            ((TilingSettingsHolder) (Object) context).globeWorld$setGlobeSettings(globeSettings);
        });
    }
}
