package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FishingHook.class)
public class FishingHookMixin {
    @WrapOperation(
            method = "shouldStopFishing",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/FishingHook;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private double globeWorld$useWrappedOwnerDistance(FishingHook hook, Entity owner, Operation<Double> original) {
        return CoordUtil.wrappedDistanceSqr(hook, owner);
    }

    @WrapOperation(
            method = "retrieve",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getX()D",
                    ordinal = 0
            )
    )
    private double globeWorld$useWrappedLootPullX(Player owner, Operation<Double> original) {
        FishingHook hook = (FishingHook) (Object) this;
        return hook.getX() + this.globeWorld$ownerPullDeltaX(owner);
    }

    @WrapOperation(
            method = "retrieve",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getZ()D",
                    ordinal = 0
            )
    )
    private double globeWorld$useWrappedLootPullZ(Player owner, Operation<Double> original) {
        FishingHook hook = (FishingHook) (Object) this;
        return hook.getZ() + this.globeWorld$ownerPullDeltaZ(owner);
    }

    @WrapOperation(
            method = "pullEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getX()D"
            )
    )
    private double globeWorld$useWrappedHookedEntityPullX(Entity owner, Operation<Double> original) {
        FishingHook hook = (FishingHook) (Object) this;
        return hook.getX() + this.globeWorld$ownerPullDeltaX(owner);
    }

    @WrapOperation(
            method = "pullEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getZ()D"
            )
    )
    private double globeWorld$useWrappedHookedEntityPullZ(Entity owner, Operation<Double> original) {
        FishingHook hook = (FishingHook) (Object) this;
        return hook.getZ() + this.globeWorld$ownerPullDeltaZ(owner);
    }

    @Unique
    private double globeWorld$ownerPullDeltaX(Entity owner) {
        FishingHook hook = (FishingHook) (Object) this;
        return CoordUtil.wrappedDeltaBlock(hook.level(), owner.getX(), hook.getX());
    }

    @Unique
    private double globeWorld$ownerPullDeltaZ(Entity owner) {
        FishingHook hook = (FishingHook) (Object) this;
        return CoordUtil.wrappedDeltaBlock(hook.level(), owner.getZ(), hook.getZ());
    }
}
