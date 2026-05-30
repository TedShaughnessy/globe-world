package globe.world.mixin;

import globe.world.util.EntityCanonicalizer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerLevelEntityTickMixin {
    @Inject(method = "tickNonPassenger", at = @At("TAIL"))
    private void canonicalizeRootEntityAfterTick(Entity entity, CallbackInfo ci) {
        EntityCanonicalizer.canonicalizeAfterTick(entity);
    }

    @Inject(method = "tickPassenger", at = @At("TAIL"))
    private void canonicalizePassengerEntityAfterTick(Entity vehicle, Entity entity, CallbackInfo ci) {
        EntityCanonicalizer.canonicalizeAfterTick(entity);
    }
}
