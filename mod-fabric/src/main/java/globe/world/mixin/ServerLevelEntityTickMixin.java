package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import globe.world.util.DimensionTiling;
import globe.world.util.EntityCanonicalizer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerLevel.class)
public class ServerLevelEntityTickMixin {
    @Shadow @Final private ServerChunkCache chunkSource;
    @Shadow @Final private List<ServerPlayer> players;

    @WrapOperation(
            method = "lambda$tick$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"
            )
    )
    private ChunkPos useTickingAliasForCanonicalEntityTickGate(Entity entity, Operation<ChunkPos> original) {
        ChunkPos entityChunk = original.call(entity);
        if (this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(entityChunk.pack())) {
            return entityChunk;
        }

        DimensionTiling tiling = DimensionTiling.forLevel(entity.level());
        if (!tiling.enabled()) {
            return entityChunk;
        }

        TopologyContext topology = TopologyContexts.forLevel(entity.level());
        ChunkPos canonicalChunk = topology.canonicalChunk(entityChunk);
        for (ServerPlayer player : this.players) {
            if (player.level() != entity.level()) {
                continue;
            }

            ChunkPos virtualChunk = topology.virtualChunkForViewer(canonicalChunk, player);
            if (this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(virtualChunk.pack())) {
                return virtualChunk;
            }
        }

        return entityChunk;
    }

    @Inject(method = "tickNonPassenger", at = @At("TAIL"))
    private void canonicalizeRootEntityAfterTick(Entity entity, CallbackInfo ci) {
        EntityCanonicalizer.canonicalizeAfterTick(entity);
    }

    @Inject(method = "tickPassenger", at = @At("TAIL"))
    private void canonicalizePassengerEntityAfterTick(Entity vehicle, Entity entity, CallbackInfo ci) {
        EntityCanonicalizer.canonicalizeAfterTick(entity);
    }
}
