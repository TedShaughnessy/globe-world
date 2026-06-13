package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologicalCollisionQueries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityQueryMixin {
    @WrapOperation(
            method = "getItemsAtAndAbove",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            )
    )
    private static List<ItemEntity> getVisibleHopperItems(
            Level level,
            Class<ItemEntity> entityClass,
            AABB aabb,
            Predicate<? super ItemEntity> selector,
            Operation<List<ItemEntity>> original,
            Level requestedLevel,
            Hopper hopper) {
        return TopologicalCollisionQueries.entitiesOfClass(level, entityClass, aabb, selector);
    }

    @WrapOperation(
            method = "getEntityContainer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            )
    )
    private static List<Entity> getVisibleEntityContainers(
            Level level,
            Entity except,
            AABB aabb,
            Predicate<? super Entity> selector,
            Operation<List<Entity>> original,
            Level requestedLevel,
            double x,
            double y,
            double z) {
        return TopologicalCollisionQueries.entities(level, except, aabb, selector);
    }
}
