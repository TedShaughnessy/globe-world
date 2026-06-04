package globe.world.mixin;

import globe.world.util.EntityCanonicalizer;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityTeleportCanonicalizationMixin {
    @Inject(
            method = "teleportSetPosition(Lnet/minecraft/world/entity/PositionMoveRotation;Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",
            at = @At("TAIL")
    )
    private void canonicalizeAfterTeleportSetPosition(
            PositionMoveRotation currentValues,
            PositionMoveRotation destination,
            Set<Relative> relatives,
            CallbackInfo ci) {
        EntityCanonicalizer.canonicalizeAfterTeleport((Entity) (Object) this);
    }

    @Inject(method = "teleportTo(DDD)V", at = @At("TAIL"))
    private void canonicalizeAfterDirectTeleport(double x, double y, double z, CallbackInfo ci) {
        EntityCanonicalizer.canonicalizeAfterTeleport((Entity) (Object) this);
    }
}
