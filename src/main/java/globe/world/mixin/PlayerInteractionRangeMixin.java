package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class PlayerInteractionRangeMixin {

    @WrapOperation(
        method = "isWithinEntityInteractionRange(Lnet/minecraft/world/phys/AABB;D)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/AABB;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    private double wrapEntityInteractionDistance(AABB box, Vec3 eyePosition, Operation<Double> original) {
        Player player = (Player) (Object) this;
        return original.call(CoordUtil.virtualAabb(player.level(), box, player.getX(), player.getZ()), eyePosition);
    }

    @WrapOperation(
        method = "isWithinBlockInteractionRange",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/AABB;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    private double wrapBlockInteractionDistance(AABB box, Vec3 eyePosition, Operation<Double> original) {
        Player player = (Player) (Object) this;
        return original.call(CoordUtil.virtualAabb(player.level(), box, player.getX(), player.getZ()), eyePosition);
    }

    @WrapOperation(
        method = "isWithinAttackRange",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/component/AttackRange;isInRange(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/AABB;D)Z"
        )
    )
    private boolean wrapAttackRangeBox(
            AttackRange range,
            net.minecraft.world.entity.LivingEntity attacker,
            AABB box,
            double buffer,
            Operation<Boolean> original,
            ItemStack weaponItem) {
        return original.call(range, attacker, CoordUtil.virtualAabb(attacker.level(), box, attacker.getX(), attacker.getZ()), buffer);
    }
}
