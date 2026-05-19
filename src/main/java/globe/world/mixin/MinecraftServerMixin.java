package globe.world.mixin;

import com.mojang.datafixers.DataFixer;
import globe.world.config.GlobeConfig;
import globe.world.config.TilingSettings;
import globe.world.config.TilingSettingsHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.Proxy;
import java.util.Optional;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void globeWorld$loadTilingSettings(
            Thread serverThread,
            LevelStorageSource.LevelStorageAccess storageSource,
            PackRepository packRepository,
            WorldStem worldStem,
            Optional<GameRules> gameRules,
            Proxy proxy,
            DataFixer fixerUpper,
            Services services,
            LevelLoadListener progressListener,
            boolean debug,
            CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        TilingSettings settings = ((TilingSettingsHolder) (Object) server.getWorldGenSettings()).globeWorld$getTilingSettings();
        GlobeConfig.setTilingSettings(settings);
    }
}
