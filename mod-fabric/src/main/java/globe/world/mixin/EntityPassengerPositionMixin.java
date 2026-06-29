package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import globe.world.util.EntityCanonicalizer;
import globe.world.util.GlobeEntityAliasing;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public class EntityPassengerPositionMixin {
    @WrapOperation(
            method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity$MoveFunction;accept(Lnet/minecraft/world/entity/Entity;DDD)V"
            )
    )
    private void keepPlayerPassengerInVisibleTile(
            Entity.MoveFunction moveFunction,
            Entity passenger,
            double x,
            double y,
            double z,
            Operation<Void> original) {
        Entity vehicle = (Entity) (Object) this;
        if (passenger instanceof ServerPlayer && EntityCanonicalizer.shouldCanonicalizeContinuously(vehicle)) {
            TopologyContext topology = TopologyContexts.forLevel(vehicle.level());
            Vec3 visiblePosition = topology.virtualBlockForViewer(
                    topology.canonicalBlock(new Vec3(x, y, z)),
                    passenger.position());
            x = visiblePosition.x();
            z = visiblePosition.z();
        }
        if (vehicle.level().isClientSide()
                && !(passenger instanceof Player)
                && !GlobeEntityAliasing.hasPlayerInStack(vehicle)
                && GlobeEntityAliasing.isWholeTileRebase(passenger.level(), passenger.position(), new Vec3(x, y, z))) {
            passenger.snapTo(x, y, z, passenger.getYRot(), passenger.getXRot());
            InterpolationHandler interpolation = passenger.getInterpolation();
            if (interpolation != null) {
                interpolation.cancel();
            }
            return;
        }
        original.call(moveFunction, passenger, x, y, z);
    }
}
