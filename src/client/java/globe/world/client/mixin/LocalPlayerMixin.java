package globe.world.client.mixin;

import globe.world.util.GlobeCurvedRaycast;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
    @Inject(
            method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void globeWorld$pickCurved(Entity cameraEntity, double blockInteractionRange, double entityInteractionRange, float partialTicks, CallbackInfoReturnable<HitResult> cir) {
        cir.setReturnValue(GlobeCurvedRaycast.pick(cameraEntity, blockInteractionRange, entityInteractionRange, partialTicks));
    }
}
