package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologicalCollisionQueries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

@Mixin(OldMinecartBehavior.class)
public class OldMinecartBehaviorCollisionMixin {
    @WrapOperation(
            method = "pushAndPickupEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            )
    )
    private List<Entity> getVisiblePushableEntities(
            Level level,
            Entity except,
            AABB hitbox,
            Predicate<? super Entity> selector,
            Operation<List<Entity>> original) {
        return TopologicalCollisionQueries.entities(level, except, hitbox, selector);
    }

    @WrapOperation(
            method = "pushAndPickupEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<Entity> getVisibleMinecartEntities(
            Level level,
            Entity except,
            AABB hitbox,
            Operation<List<Entity>> original) {
        return TopologicalCollisionQueries.entities(level, except, hitbox);
    }
}
