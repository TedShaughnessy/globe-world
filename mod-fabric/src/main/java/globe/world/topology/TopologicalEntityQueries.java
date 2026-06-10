package globe.world.topology;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
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
        List<T> entities = new ArrayList<>();
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        addAll(entities, seen, level.getEntitiesOfClass(entityClass, visibleBox, selector));

        TopologyContext context = TopologyContexts.forLevel(level);
        if (!context.enabled()) {
            return entities;
        }

        for (AABB canonicalBox : canonicalQueryBoxes(context, visibleBox)) {
            if (canonicalBox != visibleBox) {
                addAll(entities, seen, level.getEntitiesOfClass(entityClass, canonicalBox, selector));
            }
        }
        addAliasPlayersOfClass(level, entityClass, visibleBox, selector, entities, seen);
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

    public static List<AABB> canonicalQueryBoxes(Level level, AABB visibleBox) {
        return canonicalQueryBoxes(TopologyContexts.forLevel(level), visibleBox);
    }

    public static List<AABB> canonicalQueryBoxes(TopologyContext context, AABB visibleBox) {
        if (!context.enabled()) {
            return List.of(visibleBox);
        }

        List<Interval> xIntervals = canonicalIntervals(context.tileSizeBlocks(), visibleBox.minX, visibleBox.maxX);
        List<Interval> zIntervals = canonicalIntervals(context.tileSizeBlocks(), visibleBox.minZ, visibleBox.maxZ);
        List<AABB> boxes = new ArrayList<>(xIntervals.size() * zIntervals.size());
        for (Interval x : xIntervals) {
            for (Interval z : zIntervals) {
                AABB canonicalBox = new AABB(x.min(), visibleBox.minY, z.min(), x.max(), visibleBox.maxY, z.max());
                if (sameBox(canonicalBox, visibleBox)) {
                    boxes.add(visibleBox);
                } else {
                    boxes.add(canonicalBox);
                }
            }
        }
        return boxes;
    }

    private static List<Interval> canonicalIntervals(int tileSize, double min, double max) {
        double canonicalMin = -tileSize / 2.0D;
        double canonicalMax = canonicalMin + tileSize;
        if (max - min >= tileSize) {
            return List.of(new Interval(canonicalMin, canonicalMax));
        }

        int firstOffset = (int)Math.floor((min - canonicalMax) / tileSize);
        int lastOffset = (int)Math.floor((max - canonicalMin) / tileSize);
        List<Interval> intervals = new ArrayList<>();
        for (int offset = firstOffset; offset <= lastOffset; offset++) {
            double shiftedMin = min - offset * (double)tileSize;
            double shiftedMax = max - offset * (double)tileSize;
            double intervalMin = Math.max(shiftedMin, canonicalMin);
            double intervalMax = Math.min(shiftedMax, canonicalMax);
            if (intervalMax > intervalMin) {
                intervals.add(new Interval(intervalMin, intervalMax));
            }
        }
        return intervals.isEmpty() ? List.of(new Interval(min, max)) : intervals;
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

    private static <T extends Entity> void addAliasPlayersOfClass(
            Level level,
            Class<T> entityClass,
            AABB visibleBox,
            Predicate<? super T> selector,
            List<T> entities,
            Set<Entity> seen) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        for (ServerPlayer player : serverLevel.players()) {
            if (seen.contains(player) || !entityClass.isInstance(player)) {
                continue;
            }
            T candidate = entityClass.cast(player);
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

    private static boolean sameBox(AABB a, AABB b) {
        return a.minX == b.minX
                && a.minY == b.minY
                && a.minZ == b.minZ
                && a.maxX == b.maxX
                && a.maxY == b.maxY
                && a.maxZ == b.maxZ;
    }

    private record Interval(double min, double max) {
    }
}
