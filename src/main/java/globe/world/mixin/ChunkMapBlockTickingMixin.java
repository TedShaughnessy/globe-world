package globe.world.mixin;

import globe.world.util.CoordUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.function.Consumer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class ChunkMapBlockTickingMixin {
    @Shadow @Final private ServerLevel level;

    @Shadow
    protected abstract ChunkHolder getVisibleChunkIfPresent(long key);

    @Inject(method = "forEachBlockTickingChunk", at = @At("HEAD"), cancellable = true)
    private void snapshotAndCanonicalizeBlockTickingChunks(Consumer<LevelChunk> tickingChunkConsumer, CallbackInfo ci) {
        LongArrayList tickingChunkKeys = new LongArrayList();
        ((ChunkMap) (Object) this).getDistanceManager().forEachEntityTickingChunk(key -> tickingChunkKeys.add(key));

        LongSet tickedCanonicalChunks = new LongOpenHashSet();
        for (long chunkKey : tickingChunkKeys) {
            ChunkHolder holder = this.getVisibleChunkIfPresent(chunkKey);
            if (holder == null) {
                continue;
            }

            LevelChunk chunk = holder.getTickingChunk();
            if (chunk == null) {
                continue;
            }

            ChunkPos canonicalPos = CoordUtil.wrapChunkPos(this.level, chunk.getPos());
            if (!tickedCanonicalChunks.add(canonicalPos.pack())) {
                continue;
            }

            LevelChunk chunkToTick = chunk;
            if (!canonicalPos.equals(chunk.getPos())) {
                chunkToTick = this.level.getChunkSource().getChunkNow(canonicalPos.x(), canonicalPos.z());
                if (chunkToTick == null) {
                    continue;
                }
            }

            tickingChunkConsumer.accept(chunkToTick);
        }

        ci.cancel();
    }
}
