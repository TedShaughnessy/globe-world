package globe.world.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class ProjectileAliasUtil {
    private ProjectileAliasUtil() {
    }

    public static Collection<EntityHitResult> addWrappedEntityHits(
            Level level,
            Entity source,
            Vec3 from,
            Vec3 to,
            AABB targetSearchArea,
            Predicate<Entity> matching,
            Collection<EntityHitResult> vanillaHits,
            ClipContext.Block clipType,
            boolean includeFromEntity) {
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (!tiling.enabled() || !(level instanceof ServerLevel serverLevel)) {
            return vanillaHits;
        }

        List<EntityHitResult> hits = new ArrayList<>(vanillaHits);
        Set<Entity> hitEntities = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (EntityHitResult hit : vanillaHits) {
            hitEntities.add(hit.getEntity());
        }

        AABB canonicalSearchArea = CoordUtil.wrapAabb(tiling, targetSearchArea);
        for (Entity entity : level.getEntities(source, canonicalSearchArea, matching)) {
            addAliasHit(level, source, from, to, matching, clipType, includeFromEntity, hits, hitEntities, entity);
        }

        for (ServerPlayer player : serverLevel.players()) {
            addAliasHit(level, source, from, to, matching, clipType, includeFromEntity, hits, hitEntities, player);
        }

        return hits;
    }

    public static <T extends Entity> List<T> addWrappedEntitiesOfClass(
            Level level,
            Class<T> entityClass,
            AABB searchArea,
            List<T> vanillaEntities) {
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (!tiling.enabled() || !(level instanceof ServerLevel serverLevel)) {
            return vanillaEntities;
        }

        List<T> entities = new ArrayList<>(vanillaEntities);
        Set<Entity> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        seen.addAll(vanillaEntities);

        AABB canonicalSearchArea = CoordUtil.wrapAabb(tiling, searchArea);
        for (T entity : level.getEntitiesOfClass(entityClass, canonicalSearchArea)) {
            addEntityIfAbsent(entities, seen, entity);
        }

        for (ServerPlayer player : serverLevel.players()) {
            if (entityClass.isInstance(player)
                    && searchArea.intersects(nearestAliasAabb(level, searchArea, player.getBoundingBox()))) {
                addEntityIfAbsent(entities, seen, entityClass.cast(player));
            }
        }

        return entities;
    }

    public static AABB nearestAliasAabb(Level level, AABB sourceArea, AABB targetBox) {
        if (!DimensionTiling.forLevel(level).enabled()) {
            return targetBox;
        }
        double sourceX = (sourceArea.minX + sourceArea.maxX) * 0.5D;
        double sourceZ = (sourceArea.minZ + sourceArea.maxZ) * 0.5D;
        return CoordUtil.virtualAabb(level, CoordUtil.wrapAabb(level, targetBox), sourceX, sourceZ);
    }

    private static <T extends Entity> void addEntityIfAbsent(List<T> entities, Set<Entity> seen, T entity) {
        if (seen.add(entity)) {
            entities.add(entity);
        }
    }

    private static void addAliasHit(
            Level level,
            Entity source,
            Vec3 from,
            Vec3 to,
            Predicate<Entity> matching,
            ClipContext.Block clipType,
            boolean includeFromEntity,
            List<EntityHitResult> hits,
            Set<Entity> hitEntities,
            Entity entity) {
        if (entity == source || hitEntities.contains(entity) || !matching.test(entity)) {
            return;
        }

        AABB canonicalBox = CoordUtil.wrapAabb(level, entity.getBoundingBox());
        AABB aliasBox = CoordUtil.virtualAabb(level, canonicalBox, from.x, from.z);
        EntityHitResult hit = findAliasHit(level, source, from, to, aliasBox, entity, clipType, includeFromEntity);
        if (hit != null) {
            hits.add(hit);
            hitEntities.add(entity);
        }
    }

    private static EntityHitResult findAliasHit(
            Level level,
            Entity source,
            Vec3 from,
            Vec3 to,
            AABB aliasBox,
            Entity entity,
            ClipContext.Block clipType,
            boolean includeFromEntity) {
        if (includeFromEntity && aliasBox.contains(from)) {
            return new EntityHitResult(entity, from);
        }

        return aliasBox.clip(from, to)
                .map(location -> new EntityHitResult(entity, location))
                .orElseGet(() -> findMarginAliasHit(level, source, from, to, aliasBox, entity, clipType));
    }

    private static EntityHitResult findMarginAliasHit(
            Level level,
            Entity source,
            Vec3 from,
            Vec3 to,
            AABB aliasBox,
            Entity entity,
            ClipContext.Block clipType) {
        float entityMargin = ProjectileUtil.computeMargin(source);
        if (entityMargin <= 0.0F) {
            return null;
        }

        return aliasBox.inflate(entityMargin).clip(from, to)
                .flatMap(outsideHitPosition -> {
                    Vec3 towardsTarget = aliasBox.getCenter();
                    BlockHitResult blockHit = level.clipIncludingBorder(
                            new ClipContext(outsideHitPosition, towardsTarget, clipType, ClipContext.Fluid.NONE, source));
                    if (blockHit.getType() != HitResult.Type.MISS) {
                        towardsTarget = blockHit.getLocation();
                    }
                    return aliasBox.clip(outsideHitPosition, towardsTarget);
                })
                .map(location -> new EntityHitResult(entity, location))
                .orElse(null);
    }
}
