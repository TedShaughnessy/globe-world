package globe.world.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.GlobeEntityAliasing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @WrapOperation(
            method = "handleEntityPositionSync",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;moveOrInterpolateTo(Lnet/minecraft/world/phys/Vec3;FF)V"
            )
    )
    private void globeWorld$snapPositionSyncRebase(Entity entity, Vec3 position, float yRot, float xRot, Operation<Void> original) {
        if (shouldSnapRebase(entity, position)) {
            entity.snapTo(position, yRot, xRot);
            cancelInterpolation(entity);
            snapMountedStackToVehicle(entity);
        } else {
            original.call(entity, position, yRot, xRot);
        }
    }

    @WrapOperation(
            method = "handleTeleportEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z"
            )
    )
    private boolean globeWorld$snapTeleportRebase(PositionMoveRotation change, Set<Relative> relatives, Entity entity, boolean interpolate, Operation<Boolean> original) {
        PositionMoveRotation currentValues = PositionMoveRotation.of(entity);
        PositionMoveRotation newValues = PositionMoveRotation.calculateAbsolute(currentValues, change, relatives);
        if (interpolate && shouldSnapRebase(entity, newValues.position())) {
            entity.snapTo(newValues.position(), newValues.yRot(), newValues.xRot());
            entity.setDeltaMovement(newValues.deltaMovement());
            cancelInterpolation(entity);
            snapMountedStackToVehicle(entity);
            return false;
        } else {
            return original.call(change, relatives, entity, interpolate);
        }
    }

    private boolean shouldSnapRebase(Entity entity, Vec3 position) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == minecraft.player || GlobeEntityAliasing.hasPlayerInStack(entity)) {
            return false;
        }
        return GlobeEntityAliasing.isWholeTileRebase(entity.level(), entity.position(), position);
    }

    private static void snapMountedStackToVehicle(Entity entity) {
        Entity root = entity.getRootVehicle();
        if (!root.isVehicle() || GlobeEntityAliasing.hasPlayerInStack(root)) {
            return;
        }

        root.getSelfAndPassengers().forEach(vehicle -> {
            for (Entity passenger : vehicle.getPassengers()) {
                vehicle.positionRider(passenger);
                passenger.setOldPosAndRot();
                cancelInterpolation(passenger);
            }
        });
    }

    private static void cancelInterpolation(Entity entity) {
        InterpolationHandler interpolation = entity.getInterpolation();
        if (interpolation != null) {
            interpolation.cancel();
        }
    }
}
