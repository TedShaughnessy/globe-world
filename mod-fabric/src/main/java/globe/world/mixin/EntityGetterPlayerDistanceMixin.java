package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityGetter.class)
public interface EntityGetterPlayerDistanceMixin {
    @WrapOperation(
        method = "getNearestPlayer(DDDDLjava/util/function/Predicate;)Lnet/minecraft/world/entity/player/Player;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;distanceToSqr(DDD)D"
        )
    )
    private double useWrappedDistanceForNearestPlayer(Player player, double x, double y, double z, Operation<Double> original) {
        return CoordUtil.wrappedDistanceSqr(player.level(), player.getX(), player.getY(), player.getZ(), x, y, z);
    }

    @WrapOperation(
        method = "hasNearbyAlivePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;distanceToSqr(DDD)D"
        )
    )
    private double useWrappedDistanceForNearbyAlivePlayer(Player player, double x, double y, double z, Operation<Double> original) {
        return CoordUtil.wrappedDistanceSqr(player.level(), player.getX(), player.getY(), player.getZ(), x, y, z);
    }
}
