package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologicalRaycasts;
import globe.world.util.ProjectileAliasUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

@Mixin(AbstractArrow.class)
public class AbstractArrowAliasCollisionMixin {
    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;clipIncludingBorder(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
            )
    )
    private BlockHitResult useTopologicalBlockClip(Level level, ClipContext context, Operation<BlockHitResult> original) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        return TopologicalRaycasts.topologicalClip(
                level,
                context.getFrom(),
                context.getTo(),
                new TopologicalRaycasts.BlockTraceOptions(
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        arrow,
                        true
                )
        ).visibleHitWithCanonicalBlock();
    }

    @WrapOperation(
            method = "findHitEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/EntityHitResult;"
            )
    )
    private EntityHitResult addWrappedSingleEntityHit(
            Level level,
            Projectile source,
            Vec3 from,
            Vec3 to,
            AABB targetSearchArea,
            Predicate<Entity> matching,
            Operation<EntityHitResult> original) {
        EntityHitResult vanillaHit = original.call(level, source, from, to, targetSearchArea, matching);
        Collection<EntityHitResult> hits = ProjectileAliasUtil.addWrappedEntityHits(
                level,
                source,
                from,
                to,
                targetSearchArea,
                matching,
                vanillaHit == null ? List.of() : List.of(vanillaHit),
                ClipContext.Block.COLLIDER,
                false);
        return hits.stream()
                .min(Comparator.comparingDouble(hit -> from.distanceToSqr(hit.getLocation())))
                .orElse(null);
    }

    @WrapOperation(
            method = "findHitEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getManyEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Z)Ljava/util/Collection;"
            )
    )
    private Collection<EntityHitResult> addWrappedEntityHits(
            Level level,
            Entity source,
            Vec3 from,
            Vec3 to,
            AABB targetSearchArea,
            Predicate<Entity> matching,
            boolean includeFromEntity,
            Operation<Collection<EntityHitResult>> original) {
        Collection<EntityHitResult> vanillaHits = original.call(
                level,
                source,
                from,
                to,
                targetSearchArea,
                matching,
                includeFromEntity);
        return ProjectileAliasUtil.addWrappedEntityHits(
                level,
                source,
                from,
                to,
                targetSearchArea,
                matching,
                vanillaHits,
                ClipContext.Block.COLLIDER,
                includeFromEntity);
    }
}
