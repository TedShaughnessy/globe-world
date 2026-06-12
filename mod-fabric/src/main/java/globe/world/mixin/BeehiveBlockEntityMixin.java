package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity.BeeReleaseStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BeehiveBlockEntity.class)
public class BeehiveBlockEntityMixin {
    @WrapOperation(
        method = "emptyAllLivingFromHive",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    private double useWrappedDistanceToReleasedBee(
            Vec3 playerPos,
            Vec3 beePos,
            Operation<Double> original,
            Player player,
            BlockState state,
            BeeReleaseStatus releaseReason) {
        return CoordUtil.wrappedDistanceSqr(player.level(), playerPos.x(), playerPos.y(), playerPos.z(), beePos.x(), beePos.y(), beePos.z());
    }
}
