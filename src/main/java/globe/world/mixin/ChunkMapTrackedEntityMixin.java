package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class ChunkMapTrackedEntityMixin {

    @WrapOperation(
        method = "updatePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private Vec3 wrapTrackingDelta(Vec3 playerPos, Vec3 entityPos, Operation<Vec3> original) {
        Vec3 delta = original.call(playerPos, entityPos);
        return new Vec3(
            CoordUtil.wrappedDeltaBlock(playerPos.x, entityPos.x),
            delta.y,
            CoordUtil.wrappedDeltaBlock(playerPos.z, entityPos.z)
        );
    }
}
