package globe.world.topology;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public final class TopologicalCollisionQueries {
    private TopologicalCollisionQueries() {
    }

    public static List<Entity> entities(Level level, @Nullable Entity except, AABB visibleBox) {
        return entities(level, except, visibleBox, EntitySelector.NO_SPECTATORS);
    }

    public static List<Entity> entities(
            Level level,
            @Nullable Entity except,
            AABB visibleBox,
            Predicate<? super Entity> selector) {
        return TopologicalEntityQueries.entities(level, except, visibleBox, selector)
                .stream()
                .filter(entity -> TopologicalEntityQueries.intersectsVisible(level, visibleBox, entity))
                .toList();
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
        return TopologicalEntityQueries.entities(level, entityType, visibleBox, selector)
                .stream()
                .filter(entity -> TopologicalEntityQueries.intersectsVisible(level, visibleBox, entity))
                .toList();
    }

    public static boolean noEntityCollision(Level level, @Nullable Entity source, AABB visibleBox) {
        if (visibleBox.getSize() < 1.0E-7) {
            return true;
        }

        Predicate<Entity> canCollide = source == null
                ? EntitySelector.CAN_BE_COLLIDED_WITH
                : EntitySelector.NO_SPECTATORS.and(source::canCollideWith);
        AABB queryBox = visibleBox.inflate(1.0E-7);
        for (Entity entity : TopologicalEntityQueries.entities(level, source, queryBox, canCollide)) {
            AABB aliasBox = TopologicalEntityQueries.nearestAliasBox(level, queryBox, entity.getBoundingBox());
            if (Shapes.joinIsNotEmpty(Shapes.create(visibleBox), Shapes.create(aliasBox), BooleanOp.AND)) {
                return false;
            }
        }
        return true;
    }

    public static List<VoxelShape> entityCollisionShapes(Level level, @Nullable Entity source, AABB visibleBox) {
        if (visibleBox.getSize() < 1.0E-7) {
            return List.of();
        }

        Predicate<Entity> canCollide = source == null
                ? EntitySelector.CAN_BE_COLLIDED_WITH
                : EntitySelector.NO_SPECTATORS.and(source::canCollideWith);
        AABB queryBox = visibleBox.inflate(1.0E-7);
        List<Entity> entities = TopologicalEntityQueries.entities(level, source, queryBox, canCollide);
        if (entities.isEmpty()) {
            return List.of();
        }

        ImmutableList.Builder<VoxelShape> shapes = ImmutableList.builderWithExpectedSize(entities.size());
        for (Entity entity : entities) {
            AABB aliasBox = TopologicalEntityQueries.nearestAliasBox(level, queryBox, entity.getBoundingBox());
            if (visibleBox.intersects(aliasBox)) {
                shapes.add(Shapes.create(aliasBox));
            }
        }
        return shapes.build();
    }
}
