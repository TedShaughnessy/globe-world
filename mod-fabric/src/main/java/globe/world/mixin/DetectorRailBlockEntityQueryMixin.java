package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologicalCollisionQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

@Mixin(DetectorRailBlock.class)
public class DetectorRailBlockEntityQueryMixin {
    @WrapOperation(
            method = "getInteractingMinecartOfType",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            )
    )
    private <T extends AbstractMinecart> List<T> getVisibleInteractingMinecarts(
            Level level,
            Class<T> type,
            AABB searchBox,
            Predicate<? super T> selector,
            Operation<List<T>> original,
            Level requestedLevel,
            BlockPos pos,
            Class<T> requestedType,
            Predicate<Entity> containerEntitySelector) {
        return TopologicalCollisionQueries.entitiesOfClass(level, type, searchBox, selector);
    }
}
