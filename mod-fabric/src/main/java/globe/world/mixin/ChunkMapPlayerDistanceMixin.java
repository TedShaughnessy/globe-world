package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologyContexts;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkMap.class)
public class ChunkMapPlayerDistanceMixin {
    @Shadow @Final private ServerLevel level;

    @WrapOperation(
        method = "playerIsCloseEnoughTo",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    private double useWrappedDistanceForBlockProximity(Vec3 playerPos, Vec3 target, Operation<Double> original) {
        return Math.sqrt(TopologyContexts.forLevel(this.level).wrappedDistanceSqr(playerPos, target));
    }
}
