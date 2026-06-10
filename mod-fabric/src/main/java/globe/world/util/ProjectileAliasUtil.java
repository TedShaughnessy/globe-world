package globe.world.util;

import globe.world.topology.TopologicalEntityQueries;
import globe.world.topology.TopologicalRaycasts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
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
        if (!tiling.enabled() || !(level instanceof ServerLevel)) {
            return vanillaHits;
        }

        List<EntityHitResult> hits = new ArrayList<>(vanillaHits);
        Set<Entity> hitEntities = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (EntityHitResult hit : vanillaHits) {
            hitEntities.add(hit.getEntity());
        }

        for (TopologicalRaycasts.EntitySweepHit hit : TopologicalRaycasts.topologicalEntitySweep(
                level,
                source,
                from,
                to,
                targetSearchArea,
                matching,
                TopologicalRaycasts.EntitySweepOptions.projectile(source, clipType, includeFromEntity))) {
            if (hitEntities.add(hit.entity())) {
                hits.add(hit.visibleHit());
            }
        }

        return hits;
    }

    public static <T extends Entity> List<T> addWrappedEntitiesOfClass(
            Level level,
            Class<T> entityClass,
            AABB searchArea,
            List<T> vanillaEntities) {
        if (!DimensionTiling.forLevel(level).enabled()) {
            return vanillaEntities;
        }

        return TopologicalEntityQueries.entitiesOfClass(level, entityClass, searchArea);
    }

    public static AABB nearestAliasAabb(Level level, AABB sourceArea, AABB targetBox) {
        return TopologicalEntityQueries.nearestAliasBox(level, sourceArea, targetBox);
    }

}
