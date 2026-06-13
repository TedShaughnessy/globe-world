package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.GlobeNaturalSpawning;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
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
        return GlobeNaturalSpawning.canSpawnEntitiesInChunkOrViewerAlias(
                level,
                pos,
                candidate -> original.call(level, candidate));
    }
}
