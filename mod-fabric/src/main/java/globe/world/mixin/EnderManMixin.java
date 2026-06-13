package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EnderMan.class)
public class EnderManMixin {
    @WrapOperation(
            method = "teleportTowards",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getX()D"
            )
    )
    private double teleportTowardsAliasX(Entity target, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition((EnderMan)(Object)this, target);
        return alias.x;
    }

    @WrapOperation(
            method = "teleportTowards",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getZ()D"
            )
    )
    private double teleportTowardsAliasZ(Entity target, Operation<Double> original) {
        Vec3 alias = ActorLocalTargets.nearestAliasPosition((EnderMan)(Object)this, target);
        return alias.z;
    }
}
