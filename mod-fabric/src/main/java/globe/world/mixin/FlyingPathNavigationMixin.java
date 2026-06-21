package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import globe.world.entity.ActorLocalTargets;
import globe.world.util.MobNavigationAliasUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Set;

@Mixin(FlyingPathNavigation.class)
public abstract class FlyingPathNavigationMixin extends PathNavigation {
    protected FlyingPathNavigationMixin(Mob mob, Level level) {
        super(mob, level);
    }

    @WrapMethod(method = "createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;")
    @Nullable
    private Path createPathToTargetAliases(Entity target, int reachRange, Operation<Path> original) {
        if (!ActorLocalTargets.canAlias(this.mob, target)) {
            return original.call(target, reachRange);
        }
        Set<BlockPos> targets = MobNavigationAliasUtil.dedupePathTargetsByNodeHash(
                this.mob,
                ActorLocalTargets.pathTargetBlockPositions(this.mob, target));
        return this.createPath(targets, 8, false, reachRange);
    }
}
