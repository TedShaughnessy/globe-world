package globe.world.mixin;

import globe.world.util.CoordUtil;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WardenSpawnTracker.class)
public class WardenSpawnTrackerMixin {
    @Inject(method = "getNearbyPlayers", at = @At("HEAD"), cancellable = true)
    private static void getWrappedNearbyPlayers(
            ServerLevel level,
            BlockPos pos,
            CallbackInfoReturnable<List<ServerPlayer>> cir) {
        Vec3 origin = Vec3.atCenterOf(pos);
        cir.setReturnValue(level.getPlayers(player ->
                !player.isSpectator()
                        && CoordUtil.wrappedDistanceSqr(level, origin.x(), origin.y(), origin.z(), player.getX(), player.getY(), player.getZ()) < 16.0 * 16.0
                        && player.isAlive()));
    }
}
