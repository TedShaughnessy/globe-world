package globe.world.mixin;

import globe.world.GlobeWorld;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public class ChunkMapTrackingDiagnosticsMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "applyChunkTrackingView", at = @At("HEAD"))
    private void logTrackingViewChange(ServerPlayer player, ChunkTrackingView next, CallbackInfo ci) {
        if (player.level() != this.level) {
            return;
        }

        ChunkTrackingView previous = player.getChunkTrackingView();
        if (previous.equals(next)) {
            return;
        }

        int[] entered = new int[1];
        int[] left = new int[1];
        ChunkTrackingView.difference(previous, next, ignored -> entered[0]++, ignored -> left[0]++);

        GlobeWorld.LOGGER.warn(
                "GW_TRACKING_VIEW player={} dimension={} playerChunk={} from={} to={} enter={} leave={}",
                player.getScoreboardName(),
                this.level.dimension().identifier(),
                format(player.chunkPosition()),
                describe(previous),
                describe(next),
                entered[0],
                left[0]
        );
    }

    private static String describe(ChunkTrackingView view) {
        if (view instanceof ChunkTrackingView.Positioned positioned) {
            return format(positioned.center()) + "/" + positioned.viewDistance();
        }
        return "empty";
    }

    private static String format(ChunkPos pos) {
        return pos.x() + "," + pos.z();
    }
}
