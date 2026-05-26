package globe.world.mixin;

import globe.world.util.CoordUtil;
import globe.world.util.WorldGenSpillover;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class LevelChunkPostProcessMixin {
    @Inject(method = "postProcessGeneration", at = @At("HEAD"), cancellable = true)
    private void skipAliasPostProcessGeneration(ServerLevel level, CallbackInfo ci) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        ChunkPos pos = chunk.getPos();
        if (CoordUtil.wrapChunk(level, pos.x()) == pos.x() && CoordUtil.wrapChunk(level, pos.z()) == pos.z()) {
            WorldGenSpillover.applyToChunk(level, chunk);
            return;
        }

        for (ShortList postProcessingSection : chunk.getPostProcessing()) {
            if (postProcessingSection != null) {
                postProcessingSection.clear();
            }
        }
        ci.cancel();
    }
}
