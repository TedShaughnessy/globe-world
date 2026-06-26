package globe.world.mixin;

import globe.world.util.GlobeCurvedProjectileAim;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public class ProjectileCurvedAimMixin {
    @Inject(method = "shootFromRotation", at = @At("HEAD"), cancellable = true)
    private void shootFromCurvedVisualAim(
            Entity source,
            float xRot,
            float yRot,
            float yOffset,
            float power,
            float uncertainty,
            CallbackInfo ci) {
        if (source instanceof Player
                && GlobeCurvedProjectileAim.shootFromVisualAim(
                (Projectile)(Object)this,
                source,
                xRot,
                yRot,
                yOffset,
                power,
                uncertainty,
                GlobeCurvedProjectileAim.DEFAULT_PLAYER_PROJECTILE_FOCUS_DISTANCE,
                GlobeCurvedProjectileAim.CLOSE_PLAYER_PROJECTILE_FOCUS_RANGE,
                1.0F)) {
            ci.cancel();
        }
    }
}
