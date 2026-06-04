package globe.world.mixin;

import globe.world.util.CanonicalChunkTickets;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public class ChunkMapCanonicalTicketMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "onFullChunkStatusChange", at = @At("HEAD"))
    private void mirrorAliasStatusToCanonicalChunk(ChunkPos pos, FullChunkStatus status, CallbackInfo ci) {
        CanonicalChunkTickets.updateAliasStatus(this.level, pos, status);
    }
}
