package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import globe.world.util.DimensionTiling;
import globe.world.util.GlobeSpawnFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.concurrent.CompletableFuture;

@Mixin(PlayerSpawnFinder.class)
public class PlayerSpawnFinderMixin {
    @WrapMethod(method = "findSpawn")
    private static CompletableFuture<Vec3> useCanonicalTileSpawnSearch(
            ServerLevel level,
            BlockPos spawnSuggestion,
            Operation<CompletableFuture<Vec3>> original) {
        return DimensionTiling.forLevel(level).enabled()
                ? GlobeSpawnFinder.findSpawn(level, spawnSuggestion)
                : original.call(level, spawnSuggestion);
    }

    @WrapMethod(method = "getSpawnPosInChunk")
    @Nullable
    private static BlockPos searchCanonicalSpawnChunk(
            ServerLevel level,
            ChunkPos chunkPos,
            Operation<BlockPos> original) {
        return DimensionTiling.forLevel(level).enabled()
                ? GlobeSpawnFinder.findSpawnPosInChunk(level, chunkPos)
                : original.call(level, chunkPos);
    }
}
