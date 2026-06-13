package globe.world.mixin;

import globe.world.util.DimensionTiling;
import globe.world.util.GlobeSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerPlayer.class)
public class ServerPlayerRespawnBlockMixin {
    @ModifyVariable(
            method = "findRespawnAndUseSpawnBlock",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1)
    private static ServerPlayer.RespawnConfig canonicalizeRespawnBlockOwner(
            ServerPlayer.RespawnConfig respawnConfig,
            ServerLevel level) {
        return DimensionTiling.forLevel(level).enabled()
                ? GlobeSpawnFinder.canonicalRespawnConfig(level, respawnConfig)
                : respawnConfig;
    }
}
