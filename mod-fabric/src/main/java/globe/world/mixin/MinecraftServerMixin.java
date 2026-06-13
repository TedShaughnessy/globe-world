package globe.world.mixin;

import com.mojang.datafixers.DataFixer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.config.GlobeConfig;
import globe.world.config.GlobeSettings;
import globe.world.config.GlobeSettingsHolder;
import globe.world.util.ChunkAliasTracker;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import globe.world.util.GlobeSpawnFinder;
import globe.world.util.GlobeDayLength;
import globe.world.util.WorldGenSpillover;
import globe.world.util.WorldGenSpilloverOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.Proxy;
import java.util.Optional;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin implements WorldGenSpilloverOwner {
    @Unique
    private final WorldGenSpillover.State globeWorld$spilloverState = new WorldGenSpillover.State();

    @Override
    public WorldGenSpillover.State globeWorld$spilloverState() {
        return this.globeWorld$spilloverState;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void globeWorld$loadGlobeSettings(
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
        GlobeSettings settings = ((GlobeSettingsHolder) (Object) server.getWorldGenSettings()).globeWorld$getGlobeSettings();
        GlobeConfig.setGlobeSettings(settings);
    }

    @Inject(method = "loadLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;prepareLevels()V"))
    private void globeWorld$applyDayLength(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        GlobeSettings settings = ((GlobeSettingsHolder) (Object) server.getWorldGenSettings()).globeWorld$getGlobeSettings();
        GlobeDayLength.applyToServer(server, settings.gameplay());
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void globeWorld$clearChunkAliasTrackerOnStop(CallbackInfo ci) {
        WorldGenSpillover.clearAll((MinecraftServer) (Object) this);
        ChunkAliasTracker.clearAll();
    }

    @WrapOperation(
            method = "setInitialSpawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/LevelData$RespawnData;of(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;FF)Lnet/minecraft/world/level/storage/LevelData$RespawnData;"
            )
    )
    private static LevelData.RespawnData globeWorld$canonicalizeInitialSpawnData(
            ResourceKey<Level> dimension,
            BlockPos pos,
            float yaw,
            float pitch,
            Operation<LevelData.RespawnData> original,
            @Local(argsOnly = true) ServerLevel level) {
        if (!DimensionTiling.forLevel(level).enabled()) {
            return original.call(dimension, pos, yaw, pitch);
        }
        return original.call(dimension, CoordUtil.wrapBlockPos(level, pos), yaw, pitch);
    }

    @WrapOperation(
            method = "setInitialSpawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/PlayerSpawnFinder;getSpawnPosInChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/core/BlockPos;"
            )
    )
    private static BlockPos globeWorld$findInitialSpawnInCanonicalTile(
            ServerLevel level,
            ChunkPos chunkPos,
            Operation<BlockPos> original) {
        return DimensionTiling.forLevel(level).enabled()
                ? GlobeSpawnFinder.findInitialSpawnInCanonicalTile(level, chunkPos)
                : original.call(level, chunkPos);
    }
}
