package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologicalCollisionQueries;
import globe.world.topology.TopologicalEntityQueries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

@Mixin(BoatItem.class)
public class BoatItemPlacementMixin {
    @WrapOperation(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            )
    )
    private List<Entity> getVisibleBoatPlacementObstructions(
            Level level,
            Entity except,
            AABB box,
            Predicate<? super Entity> selector,
            Operation<List<Entity>> original,
            Level requestedLevel,
            Player player,
            InteractionHand hand) {
        return TopologicalCollisionQueries.entities(level, except, box, selector);
    }

    @WrapOperation(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
            )
    )
    private AABB getVisibleBoatObstructionBox(
            Entity entity,
            Operation<AABB> original,
            Level level,
            Player player,
            InteractionHand hand) {
        Vec3 eye = player.getEyePosition();
        AABB eyeBox = new AABB(eye.x, eye.y, eye.z, eye.x, eye.y, eye.z);
        return TopologicalEntityQueries.nearestAliasBox(level, eyeBox, original.call(entity));
    }

    @WrapOperation(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"
            )
    )
    private boolean boatHasNoVisibleEntityCollision(
            Level level,
            Entity entity,
            AABB box,
            Operation<Boolean> original,
            Level requestedLevel,
            Player player,
            InteractionHand hand) {
        return original.call(level, entity, box)
                && TopologicalCollisionQueries.noEntityCollision(level, entity, box);
    }
}
