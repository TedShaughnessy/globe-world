package globe.world.mixin;

import globe.world.util.PortalDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PortalProcessor.class)
public class PortalProcessorMixin {
    @Shadow private BlockPos entryPosition;
    @Shadow private int portalTime;
    @Shadow private boolean insidePortalThisTick;

    @Inject(method = "processPortalTeleportation", at = @At("RETURN"))
    private void logPortalProgress(
            ServerLevel serverLevel,
            Entity entity,
            boolean allowedToTeleport,
            CallbackInfoReturnable<Boolean> cir) {
        PortalDiagnostics.portalProcess(
                serverLevel,
                entity,
                this.entryPosition,
                this.portalTime,
                this.insidePortalThisTick,
                allowedToTeleport,
                cir.getReturnValue()
        );
    }
}
