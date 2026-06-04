package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.DimensionTiling;
import globe.world.util.WaypointPacketUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.Waypoint;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.waypoints.WaypointTransmitter$EntityAzimuthConnection")
public class WaypointAzimuthConnectionMixin {
    @Shadow
    @Final
    private LivingEntity source;

    @Shadow
    @Final
    private ServerPlayer receiver;

    @Shadow
    private float lastAngle;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void initializeWrappedAngle(
            LivingEntity source,
            Waypoint.Icon icon,
            ServerPlayer receiver,
            CallbackInfo ci) {
        if (DimensionTiling.forLevel(source.level()).enabled()) {
            this.lastAngle = WaypointPacketUtil.azimuthAngle(source, receiver);
        }
    }

    @WrapOperation(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 useWrappedAzimuthDirection(Vec3 receiverPos, Vec3 sourcePos, Operation<Vec3> original) {
        if (!DimensionTiling.forLevel(this.source.level()).enabled()) {
            return original.call(receiverPos, sourcePos);
        }

        float currentAngle = WaypointPacketUtil.azimuthAngle(this.source, this.receiver);
        return new Vec3(Math.sin(currentAngle), 0.0, -Math.cos(currentAngle));
    }

    @WrapOperation(
            method = "isBroken",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/waypoints/WaypointTransmitter;doesSourceIgnoreReceiver(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerPlayer;)Z"
            )
    )
    private boolean useWrappedIgnoreCheck(
            LivingEntity source,
            ServerPlayer receiver,
            Operation<Boolean> original) {
        if (!DimensionTiling.forLevel(source.level()).enabled()) {
            return original.call(source, receiver);
        }
        return WaypointPacketUtil.doesSourceIgnoreReceiver(source, receiver);
    }

    @WrapOperation(
            method = "isBroken",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/waypoints/WaypointTransmitter;isReallyFar(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerPlayer;)Z"
            )
    )
    private boolean useWrappedReallyFarCheck(
            LivingEntity source,
            ServerPlayer receiver,
            Operation<Boolean> original) {
        if (!DimensionTiling.forLevel(source.level()).enabled()) {
            return original.call(source, receiver);
        }
        return WaypointPacketUtil.isReallyFar(source, receiver);
    }

    @WrapOperation(
            method = "isBroken",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/waypoints/WaypointTransmitter;isChunkVisible(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/server/level/ServerPlayer;)Z"
            )
    )
    private boolean useWrappedChunkVisibility(
            ChunkPos chunk,
            ServerPlayer receiver,
            Operation<Boolean> original) {
        if (!DimensionTiling.forLevel(this.source.level()).enabled()) {
            return original.call(chunk, receiver);
        }
        return WaypointPacketUtil.isChunkVisible(chunk, receiver);
    }
}
