package globe.world.topology;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class TopologicalEntityQueries {
    private TopologicalEntityQueries() {
    }

    public static List<Entity> entities(Level level, Entity except, AABB visibleBox) {
        return entities(level, except, visibleBox, EntitySelector.NO_SPECTATORS);
    }

    public static List<Entity> entities(Level level, Entity except, AABB visibleBox, Predicate<? super Entity> selector) {
        List<Entity> entities = new ArrayList<>();
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        addAll(entities, seen, level.getEntities(except, visibleBox, selector));

        TopologyContext context = TopologyContexts.forLevel(level);
        if (!context.enabled()) {
            return entities;
        }

        for (AABB canonicalBox : canonicalQueryBoxes(context, visibleBox)) {
            if (canonicalBox != visibleBox) {
                addAll(entities, seen, level.getEntities(except, canonicalBox, selector));
            }
        }
        addAliasPlayers(level, except, visibleBox, selector, entities, seen);
        return entities;
    }

    public static <T extends Entity> List<T> entitiesOfClass(Level level, Class<T> entityClass, AABB visibleBox) {
        return entitiesOfClass(level, entityClass, visibleBox, EntitySelector.NO_SPECTATORS);
    }

    public static <T extends Entity> List<T> entitiesOfClass(
            Level level,
            Class<T> entityClass,
            AABB visibleBox,
            Predicate<? super T> selector) {
        return entities(level, EntityTypeTest.forClass(entityClass), visibleBox, selector);
    }

    public static <T extends Entity> List<T> entities(
            Level level,
            EntityTypeTest<Entity, T> entityType,
            AABB visibleBox,
            Predicate<? super T> selector) {
        List<T> entities = new ArrayList<>();
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        addAll(entities, seen, level.getEntities(entityType, visibleBox, selector));

        TopologyContext context = TopologyContexts.forLevel(level);
        if (!context.enabled()) {
            return entities;
        }

        for (AABB canonicalBox : canonicalQueryBoxes(context, visibleBox)) {
            if (canonicalBox != visibleBox) {
                addAll(entities, seen, level.getEntities(entityType, canonicalBox, selector));
            }
        }
        addAliasPlayersOfType(level, entityType, visibleBox, selector, entities, seen);
        return entities;
    }

    public static AABB nearestAliasBox(Level level, AABB sourceBox, AABB targetBox) {
        TopologyContext context = TopologyContexts.forLevel(level);
        if (!context.enabled()) {
            return targetBox;
        }

        Vec3 sourceCenter = sourceBox.getCenter();
        return context.virtualBoxForViewer(context.canonicalBox(targetBox), sourceCenter);
    }

    public static AABB nearestAliasBox(Level level, BlockPos actorBlock, AABB targetBox) {
        TopologyContext context = TopologyContexts.forLevel(level);
        if (!context.enabled()) {
            return targetBox;
        }

        return context.virtualBoxForViewer(context.canonicalBox(targetBox), actorBlock.getCenter());
    }

    public static boolean intersectsVisible(Level level, AABB visibleQueryBox, Entity entity) {
        return visibleQueryBox.intersects(nearestAliasBox(level, visibleQueryBox, entity.getBoundingBox()));
    }

    public static double distanceSqrToVisibleBox(Level level, Vec3 visibleOrigin, Entity entity) {
        AABB originBox = new AABB(
                visibleOrigin.x,
                visibleOrigin.y,
                visibleOrigin.z,
                visibleOrigin.x,
                visibleOrigin.y,
                visibleOrigin.z);
        return nearestAliasBox(level, originBox, entity.getBoundingBox()).distanceToSqr(visibleOrigin);
    }

    public static List<AABB> canonicalQueryBoxes(Level level, AABB visibleBox) {
        return canonicalQueryBoxes(TopologyContexts.forLevel(level), visibleBox);
    }

    public static List<AABB> canonicalQueryBoxes(TopologyContext context, AABB visibleBox) {
        if (!context.enabled()) {
            return List.of(visibleBox);
        }
        return TileGeometry.create(context.tiling()).canonicalQueryBoxes(visibleBox);
    }

    private static void addAliasPlayers(
            Level level,
            Entity except,
            AABB visibleBox,
            Predicate<? super Entity> selector,
            List<Entity> entities,
            Set<Entity> seen) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        for (ServerPlayer player : serverLevel.players()) {
            if (player == except || seen.contains(player) || !selector.test(player)) {
                continue;
            }
            if (visibleBox.intersects(nearestAliasBox(level, visibleBox, player.getBoundingBox()))) {
                add(entities, seen, player);
            }
        }
    }

    private static <T extends Entity> void addAliasPlayersOfType(
            Level level,
            EntityTypeTest<Entity, T> entityType,
            AABB visibleBox,
            Predicate<? super T> selector,
            List<T> entities,
            Set<Entity> seen) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        for (ServerPlayer player : serverLevel.players()) {
            if (seen.contains(player)) {
                continue;
            }
            T candidate = entityType.tryCast(player);
            if (candidate == null) {
                continue;
            }
            if (!selector.test(candidate)) {
                continue;
            }
            if (visibleBox.intersects(nearestAliasBox(level, visibleBox, player.getBoundingBox()))) {
                add(entities, seen, candidate);
            }
        }
    }

    private static <T extends Entity> void addAll(List<T> entities, Set<Entity> seen, List<? extends T> candidates) {
        for (T candidate : candidates) {
            add(entities, seen, candidate);
        }
    }

    private static <T extends Entity> void add(List<T> entities, Set<Entity> seen, T entity) {
        if (seen.add(entity)) {
            entities.add(entity);
        }
    }

}
