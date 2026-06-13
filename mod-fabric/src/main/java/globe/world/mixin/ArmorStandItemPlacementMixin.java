package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologicalCollisionQueries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ArmorStandItem.class)
public class ArmorStandItemPlacementMixin {
    @WrapOperation(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<Entity> getVisibleArmorStandPlacementObstructions(
            Level level,
            Entity except,
            AABB box,
            Operation<List<Entity>> original,
            UseOnContext context) {
        return TopologicalCollisionQueries.entities(level, except, box);
    }

    @WrapOperation(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"
            )
    )
    private boolean armorStandHasNoVisibleEntityCollision(
            Level level,
            Entity entity,
            AABB box,
            Operation<Boolean> original,
            UseOnContext context) {
        return original.call(level, entity, box)
                && TopologicalCollisionQueries.noEntityCollision(level, entity, box);
    }
}
