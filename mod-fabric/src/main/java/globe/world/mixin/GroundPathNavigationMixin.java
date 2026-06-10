package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import globe.world.entity.ActorLocalTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(GroundPathNavigation.class)
public abstract class GroundPathNavigationMixin extends PathNavigation {
    @Shadow
    private boolean canPathToTargetsBelowSurface;

    @Shadow
    abstract BlockPos findSurfacePosition(LevelChunk chunk, BlockPos pos, int reachRange);

    protected GroundPathNavigationMixin(Mob mob, Level level) {
        super(mob, level);
    }

    @WrapMethod(method = "createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;")
    @Nullable
    private Path createPathToTargetAliases(Entity target, int reachRange, Operation<Path> original) {
        if (!ActorLocalTargets.canAlias(this.mob, target)) {
            return original.call(target, reachRange);
        }

        Set<BlockPos> targets = this.globeWorld$surfaceAdjustedAliasTargets(target, reachRange);
        if (targets.isEmpty()) {
            return null;
        }
        return this.createPath(targets, 8, false, reachRange);
    }

    @Unique
    private Set<BlockPos> globeWorld$surfaceAdjustedAliasTargets(Entity target, int reachRange) {
        Set<BlockPos> adjusted = new LinkedHashSet<>();
        for (BlockPos pos : ActorLocalTargets.pathTargetBlockPositions(this.mob, target)) {
            LevelChunk chunk = this.level.getChunkSource().getChunkNow(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
            if (chunk == null) {
                continue;
            }
            adjusted.add(this.canPathToTargetsBelowSurface ? pos : this.findSurfacePosition(chunk, pos, reachRange));
        }
        return adjusted;
    }
}
