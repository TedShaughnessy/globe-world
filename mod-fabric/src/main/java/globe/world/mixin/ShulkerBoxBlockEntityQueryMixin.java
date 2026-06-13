package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologicalCollisionQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShulkerBoxBlock.class)
public class ShulkerBoxBlockEntityQueryMixin {
    @WrapOperation(
            method = "canOpen",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/phys/AABB;)Z"
            )
    )
    private static boolean shulkerLidHasNoVisibleEntityCollision(
            Level level,
            AABB lidOpenBoundingBox,
            Operation<Boolean> original,
            BlockState state,
            Level requestedLevel,
            BlockPos pos,
            ShulkerBoxBlockEntity blockEntity) {
        return original.call(level, lidOpenBoundingBox)
                && TopologicalCollisionQueries.noEntityCollision(level, null, lidOpenBoundingBox);
    }
}
