package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import globe.world.util.AiAliasUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;

@Mixin(PathNavigation.class)
public abstract class PathNavigationMixin {
    @Shadow
    protected Mob mob;

    @Shadow
    @Nullable
    protected abstract Path createPath(Set<BlockPos> targets, int radiusOffset, boolean above, int reachRange);

    @WrapMethod(method = "createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;")
    @Nullable
    private Path createPathToTargetAliases(Entity target, int reachRange, Operation<Path> original) {
        if (!AiAliasUtil.canAlias(this.mob, target)) {
            return original.call(target, reachRange);
        }
        return this.createPath(AiAliasUtil.pathTargetBlockPositions(this.mob, target), 16, true, reachRange);
    }
}
