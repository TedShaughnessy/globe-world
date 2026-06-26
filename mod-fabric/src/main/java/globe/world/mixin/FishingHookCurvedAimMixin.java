package globe.world.mixin;

import globe.world.util.GlobeCurvedProjectileAim;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHook.class)
public class FishingHookCurvedAimMixin {
    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V", at = @At("TAIL"))
    private void curveInitialFishingCast(Player player, Level level, int luck, int lureSpeed, CallbackInfo ci) {
        Projectile hook = (Projectile)(Object)this;
        Vec3 vanillaMovement = hook.getDeltaMovement();
        GlobeCurvedProjectileAim.preserveSpeedFromVisualDirection(
                hook,
                player,
                vanillaMovement,
                GlobeCurvedProjectileAim.DEFAULT_PLAYER_PROJECTILE_FOCUS_DISTANCE,
                GlobeCurvedProjectileAim.CLOSE_PLAYER_PROJECTILE_FOCUS_RANGE,
                1.0F
        );
    }
}
