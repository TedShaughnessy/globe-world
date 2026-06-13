package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologicalCollisionQueries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

@Mixin(ItemEntity.class)
public class ItemEntityMergeMixin {
    @WrapOperation(
            method = "mergeWithNeighbours",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            )
    )
    private List<ItemEntity> getVisibleMergeableItems(
            Level level,
            Class<ItemEntity> entityClass,
            AABB box,
            Predicate<? super ItemEntity> selector,
            Operation<List<ItemEntity>> original) {
        return TopologicalCollisionQueries.entitiesOfClass(level, entityClass, box, selector);
    }
}
