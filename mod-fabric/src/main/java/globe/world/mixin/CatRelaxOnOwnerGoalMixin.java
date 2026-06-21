package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.entity.ActorLocalTargets;
import globe.world.topology.TopologicalEntityQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(targets = "net.minecraft.world.entity.animal.feline.Cat$CatRelaxOnOwnerGoal")
public class CatRelaxOnOwnerGoalMixin {
    @Shadow
    @Final
    private Cat cat;

    @WrapOperation(
            method = {"canUse", "tick"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/feline/Cat;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private double globeWorld$useAliasOwnerDistance(Cat cat, Entity owner, Operation<Double> original) {
        return ActorLocalTargets.distanceToSqr(cat, owner);
    }

    @WrapOperation(
            method = "canUse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;blockPosition()Lnet/minecraft/core/BlockPos;"
            )
    )
    private BlockPos globeWorld$useAliasOwnerBedPosition(Player owner, Operation<BlockPos> original) {
        return ActorLocalTargets.nearestAliasBlockPos(this.cat, owner);
    }

    @WrapOperation(
            method = "spaceIsOccupied",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<Cat> globeWorld$getVisibleRelaxingCats(
            Level level,
            Class<Cat> catClass,
            AABB box,
            Operation<List<Cat>> original) {
        return TopologicalEntityQueries.entitiesOfClass(level, catClass, box);
    }
}
