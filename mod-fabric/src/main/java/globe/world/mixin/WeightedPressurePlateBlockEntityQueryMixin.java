package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologicalCollisionQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WeightedPressurePlateBlock.class)
public class WeightedPressurePlateBlockEntityQueryMixin {
    @WrapOperation(
            method = "getSignalStrength",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/BasePressurePlateBlock;getEntityCount(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/AABB;Ljava/lang/Class;)I"
            )
    )
    private int countVisibleWeightedPressurePlateEntities(
            Level level,
            AABB entityDetectionBox,
            Class<? extends Entity> entityClass,
            Operation<Integer> original,
            Level requestedLevel,
            BlockPos pos) {
        return TopologicalCollisionQueries.entitiesOfClass(
                level,
                entityClass,
                entityDetectionBox,
                EntitySelector.NO_SPECTATORS.and(entity -> !entity.isIgnoringBlockTriggers())).size();
    }
}
