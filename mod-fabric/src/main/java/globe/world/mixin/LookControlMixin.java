package globe.world.mixin;

import globe.world.entity.ActorLocalTargets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LookControl.class)
public abstract class LookControlMixin {
    @Shadow
    protected Mob mob;

    @Shadow
    public abstract void setLookAt(double x, double y, double z);

    @Shadow
    public abstract void setLookAt(double x, double y, double z, float yMaxRotSpeed, float xMaxRotAngle);

    @Inject(method = "setLookAt(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void setLookAtEntityAlias(Entity target, CallbackInfo ci) {
        if (!ActorLocalTargets.canAlias(this.mob, target)) {
            return;
        }
        Vec3 alias = ActorLocalTargets.nearestAliasEyePosition(this.mob, target);
        this.setLookAt(alias.x, alias.y, alias.z);
        ci.cancel();
    }

    @Inject(method = "setLookAt(Lnet/minecraft/world/entity/Entity;FF)V", at = @At("HEAD"), cancellable = true)
    private void setLookAtEntityAlias(Entity target, float yMaxRotSpeed, float xMaxRotAngle, CallbackInfo ci) {
        if (!ActorLocalTargets.canAlias(this.mob, target)) {
            return;
        }
        Vec3 alias = ActorLocalTargets.nearestAliasEyePosition(this.mob, target);
        this.setLookAt(alias.x, alias.y, alias.z, yMaxRotSpeed, xMaxRotAngle);
        ci.cancel();
    }
}
