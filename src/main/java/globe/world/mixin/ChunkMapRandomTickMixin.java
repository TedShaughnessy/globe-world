package globe.world.mixin;

import globe.world.util.CoordUtil;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ChunkMapRandomTickMixin {
    @Unique
    private final Set<Long> globeWorld$randomTickedCanonicalChunks = new HashSet<>();

    @Unique
    private long globeWorld$randomTickGameTime = Long.MIN_VALUE;

    @Unique
    private boolean globeWorld$runningCanonicalRandomTick;

    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void tickCanonicalChunkOnce(
            LevelChunk chunk,
            int tickSpeed,
            CallbackInfo ci) {
        if (globeWorld$runningCanonicalRandomTick) {
            return;
        }

        ServerLevel level = (ServerLevel) (Object) this;
        long gameTime = level.getGameTime();
        if (globeWorld$randomTickGameTime != gameTime) {
            globeWorld$randomTickGameTime = gameTime;
            globeWorld$randomTickedCanonicalChunks.clear();
        }

        ChunkPos canonicalPos = CoordUtil.wrapChunkPos(chunk.getPos());
        if (!globeWorld$randomTickedCanonicalChunks.add(canonicalPos.pack())) {
            ci.cancel();
            return;
        }

        if (canonicalPos.equals(chunk.getPos())) {
            return;
        }

        LevelChunk canonicalChunk = level.getChunkSource().getChunkNow(canonicalPos.x(), canonicalPos.z());
        if (canonicalChunk != null) {
            globeWorld$runningCanonicalRandomTick = true;
            try {
                level.tickChunk(canonicalChunk, tickSpeed);
            } finally {
                globeWorld$runningCanonicalRandomTick = false;
            }
        }

        ci.cancel();
    }
}
