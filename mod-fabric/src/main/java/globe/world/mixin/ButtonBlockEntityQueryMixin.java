package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologicalCollisionQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ButtonBlock.class)
public class ButtonBlockEntityQueryMixin {
    @WrapOperation(
            method = "checkPressed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<AbstractArrow> getVisibleButtonArrows(
            Level level,
            Class<AbstractArrow> arrowClass,
            AABB box,
            Operation<List<AbstractArrow>> original,
            BlockState state,
            Level requestedLevel,
            BlockPos pos) {
        return TopologicalCollisionQueries.entitiesOfClass(level, arrowClass, box, EntitySelector.NO_SPECTATORS);
    }
}
