package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologicalCollisionQueries;
import globe.world.topology.TopologicalEntityQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

@Mixin(PistonMovingBlockEntity.class)
public class PistonMovingBlockEntityQueryMixin {
    @WrapOperation(
            method = "moveCollidedEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private static List<Entity> getVisiblePistonCollisionEntities(
            Level level,
            Entity except,
            AABB movementBox,
            Operation<List<Entity>> original,
            Level requestedLevel,
            BlockPos pos,
            float newProgress,
            PistonMovingBlockEntity self) {
        return TopologicalCollisionQueries.entities(level, except, movementBox);
    }

    @WrapOperation(
            method = "moveStuckEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            )
    )
    private static List<Entity> getVisibleStickyPistonEntities(
            Level level,
            Entity except,
            AABB stickyBox,
            Predicate<? super Entity> selector,
            Operation<List<Entity>> original,
            Level requestedLevel,
            BlockPos pos,
            float newProgress,
            PistonMovingBlockEntity self) {
        return TopologicalCollisionQueries.entities(level, except, stickyBox, entity -> matchesVisibleStickyCriteria(level, stickyBox, entity, pos));
    }

    @WrapOperation(
            method = "moveCollidedEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
            )
    )
    private static AABB getVisibleCollidedPistonEntityBox(
            Entity entity,
            Operation<AABB> original,
            Level level,
            BlockPos pos,
            float newProgress,
            PistonMovingBlockEntity self) {
        return TopologicalEntityQueries.nearestAliasBox(level, pos, original.call(entity));
    }

    @WrapOperation(
            method = "fixEntityWithinPistonBase",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
            )
    )
    private static AABB getVisiblePistonBaseEntityBox(
            Entity entity,
            Operation<AABB> original,
            BlockPos pos,
            Entity pushedEntity,
            net.minecraft.core.Direction direction,
            double deltaProgress) {
        return TopologicalEntityQueries.nearestAliasBox(entity.level(), pos, original.call(entity));
    }

    private static boolean matchesVisibleStickyCriteria(Level level, AABB stickyBox, Entity entity, BlockPos pos) {
        AABB aliasBox = TopologicalEntityQueries.nearestAliasBox(level, stickyBox, entity.getBoundingBox());
        double aliasCenterX = (aliasBox.minX + aliasBox.maxX) * 0.5;
        double aliasCenterZ = (aliasBox.minZ + aliasBox.maxZ) * 0.5;
        return entity.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.NORMAL
                && entity.onGround()
                && (entity.isSupportedBy(pos)
                || aliasCenterX >= stickyBox.minX && aliasCenterX <= stickyBox.maxX
                && aliasCenterZ >= stickyBox.minZ && aliasCenterZ <= stickyBox.maxZ);
    }
}
