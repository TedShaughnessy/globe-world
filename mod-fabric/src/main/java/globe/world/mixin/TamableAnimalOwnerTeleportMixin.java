package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TamableAnimal.class)
public class TamableAnimalOwnerTeleportMixin {
    @WrapOperation(
            method = "shouldTryTeleportToOwner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/TamableAnimal;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private double globeWorld$useAliasOwnerTeleportDistance(TamableAnimal tamable, Entity owner, Operation<Double> original) {
        return ActorLocalTargets.distanceToSqr(tamable, owner);
    }

    @WrapOperation(
            method = "tryToTeleportToOwner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;blockPosition()Lnet/minecraft/core/BlockPos;"
            )
    )
    private BlockPos globeWorld$teleportAroundNearestOwnerAlias(LivingEntity owner, Operation<BlockPos> original) {
        TamableAnimal tamable = (TamableAnimal)(Object)this;
        if (!ActorLocalTargets.canAlias(tamable, owner)) {
            return original.call(owner);
        }

        TopologyContext topology = TopologyContexts.forLevel(tamable.level());
        return topology.virtualBlockForViewer(topology.canonicalBlock(original.call(owner)), tamable.position());
    }
}
