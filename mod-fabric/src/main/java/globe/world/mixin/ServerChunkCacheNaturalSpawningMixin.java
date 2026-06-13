package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheNaturalSpawningMixin {
    @WrapOperation(
            method = "tickSpawningChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;canSpawnEntitiesInChunk(Lnet/minecraft/world/level/ChunkPos;)Z"
            )
    )
    private boolean canSpawnInViewerAliasChunk(ServerLevel level, ChunkPos pos, Operation<Boolean> original) {
        if (original.call(level, pos)) {
            return true;
        }

        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled()) {
            return false;
        }

        ChunkPos canonicalPos = topology.canonicalChunk(pos);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.level() != level) {
                continue;
            }

            ChunkPos viewerAlias = topology.virtualChunkForViewer(canonicalPos, player);
            if (!viewerAlias.equals(canonicalPos) && original.call(level, viewerAlias)) {
                return true;
            }
        }

        return false;
    }
}
