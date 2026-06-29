package globe.world.entity;

import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import globe.world.topology.TopologicalEntityQueries;
import globe.world.topology.TopologicalRaycasts;
import globe.world.topology.TileGeometry;
import globe.world.util.DimensionTiling;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class ActorLocalTargets {
    private static final int ENTITY_TARGET_ALIAS_RADIUS = 1;

    private ActorLocalTargets() {
    }

    public static ActorLocalTargetView view(Entity actor, Entity target) {
        boolean sameLevel = actor.level() == target.level();
        boolean aliasingEnabled = canAlias(actor, target);
        TopologyContext context = TopologyContexts.forLevel(target.level());
        Vec3 canonicalPosition = context.canonicalBlock(new Vec3(target.getX(), target.getY(), target.getZ()));
        AABB canonicalBox = context.canonicalBox(target.getBoundingBox());
        return new ActorLocalTargetView(
                actor,
                target,
                canonicalPosition,
                position(actor, target),
                canonicalBox,
                box(actor, target),
                distanceToSqr(actor, target),
                horizontalDistanceToSqr(actor, target),
                sameLevel,
                aliasingEnabled
        );
    }

    public static Vec3 position(Entity actor, Entity target) {
        return nearestAliasPosition(actor, target);
    }

    public static Vec3 eyePosition(LivingEntity actor, Entity target) {
        return nearestAliasEyePosition(actor, target);
    }

    public static AABB box(Entity actor, Entity target) {
        return nearestAliasBoundingBox(actor, target);
    }

    public static List<Entity> entitiesInActorRange(
            Entity actor,
            Entity except,
            AABB actorLocalBox,
            Predicate<? super Entity> selector) {
        return TopologicalEntityQueries.entities(actor.level(), except, actorLocalBox, selector);
    }

    public static <T extends Entity> List<T> targetsInActorRange(
            Entity actor,
            Class<T> entityClass,
            AABB actorLocalBox,
            Predicate<? super T> selector) {
        return TopologicalEntityQueries.entitiesOfClass(actor.level(), entityClass, actorLocalBox, selector);
    }

    public static <T extends LivingEntity> T nearestTarget(
            LivingEntity actor,
            Iterable<? extends T> candidates,
            Predicate<? super T> selector) {
        return java.util.stream.StreamSupport.stream(candidates.spliterator(), false)
                .filter(selector)
                .min(Comparator.comparingDouble(candidate -> distanceToSqr(actor, candidate)))
                .orElse(null);
    }

    public static List<Entity> entitiesInBox(Level level, Entity except, AABB visibleBox, Predicate<? super Entity> selector) {
        return TopologicalEntityQueries.entities(level, except, visibleBox, selector);
    }

    public static <T extends Entity> List<T> entitiesOfClassInBox(
            Level level,
            Class<T> entityClass,
            AABB visibleBox,
            Predicate<? super T> selector) {
        return TopologicalEntityQueries.entitiesOfClass(level, entityClass, visibleBox, selector);
    }

    public static double distanceToSqr(Entity actor, Entity target) {
        if (!canAlias(actor, target)) {
            return actor.distanceToSqr(target);
        }
        return distanceToSqr(actor, target.getX(), target.getY(), target.getZ());
    }

    public static double distanceToSqr(Entity actor, double targetX, double targetY, double targetZ) {
        if (!enabled(actor.level())) {
            return actor.distanceToSqr(targetX, targetY, targetZ);
        }
        Vec3 alias = nearestAliasPosition(
                actor.level(),
                actor.getX(),
                actor.getY(),
                actor.getZ(),
                targetX,
                targetY,
                targetZ);
        return actor.distanceToSqr(alias.x, alias.y, alias.z);
    }

    public static boolean hasLineOfSight(LivingEntity actor, Entity target) {
        return !canAlias(actor, target)
                ? actor.hasLineOfSight(target)
                : aliasLineOfSight(actor, target);
    }

    public static Set<BlockPos> pathTargets(Mob actor, Entity target) {
        return pathTargetBlockPositions(actor, target);
    }

    public static boolean enabled(Level level) {
        return DimensionTiling.forLevel(level).enabled();
    }

    public static boolean canAlias(Entity actor, Entity target) {
        return actor.level() == target.level() && enabled(actor.level());
    }

    public static Vec3 nearestAliasPosition(Entity actor, Entity target) {
        if (!canAlias(actor, target)) {
            return target.position();
        }
        return nearestAliasPosition(
                actor.level(),
                actor.getX(),
                actor.getY(),
                actor.getZ(),
                target.getX(),
                target.getY(),
                target.getZ());
    }

    public static Vec3 nearestAliasPosition(Entity actor, Entity target, double originX, double originY, double originZ) {
        if (!canAlias(actor, target)) {
            return target.position();
        }
        return nearestAliasPosition(
                actor.level(),
                originX,
                originY,
                originZ,
                target.getX(),
                target.getY(),
                target.getZ());
    }

    public static Vec3 nearestAliasPosition(Entity actor, Entity target, Vec3 origin) {
        return nearestAliasPosition(actor, target, origin.x, origin.y, origin.z);
    }

    public static Vec3 nearestAliasEyePosition(Entity actor, Entity target) {
        if (!canAlias(actor, target)) {
            return new Vec3(target.getX(), target.getEyeY(), target.getZ());
        }
        return nearestAliasPosition(
                actor.level(),
                actor.getX(),
                actor.getY(),
                actor.getZ(),
                target.getX(),
                target.getEyeY(),
                target.getZ());
    }

    public static Vec3 nearestAliasPosition(
            Level level,
            double actorX,
            double actorY,
            double actorZ,
            double targetX,
            double targetY,
            double targetZ) {
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (!tiling.enabled()) {
            return new Vec3(targetX, targetY, targetZ);
        }

        TopologyContext topology = TopologyContexts.forLevel(level);
        Vec3 canonical = topology.canonicalBlock(new Vec3(targetX, targetY, targetZ));
        return topology.virtualBlockForViewer(canonical, new Vec3(actorX, actorY, actorZ));
    }

    public static BlockPos nearestAliasBlockPos(Entity actor, Entity target) {
        if (!canAlias(actor, target)) {
            return target.blockPosition();
        }
        Vec3 alias = nearestAliasPosition(actor, target);
        return BlockPos.containing(alias.x, alias.y, alias.z);
    }

    public static Set<BlockPos> aliasBlockPositions(Entity actor, Entity target, int tileRadius) {
        if (!canAlias(actor, target)) {
            return Set.of(target.blockPosition());
        }

        DimensionTiling tiling = DimensionTiling.forLevel(actor.level());
        BlockPos center = nearestAliasBlockPos(actor, target);
        if (tileRadius <= 0) {
            return Set.of(center);
        }

        Set<BlockPos> positions = new LinkedHashSet<>();
        AABB unitBox = AABB.ofSize(center.getCenter(), 1.0D, 1.0D, 1.0D);
        for (AABB aliasBox : TileGeometry.create(tiling).nearbyAliasBoxes(unitBox, actor.position(), tileRadius)) {
            positions.add(BlockPos.containing(aliasBox.getCenter()));
        }
        return positions;
    }

    public static Set<BlockPos> pathTargetBlockPositions(Mob actor, Entity target) {
        if (!canAlias(actor, target)) {
            return Set.of(target.blockPosition());
        }

        DimensionTiling tiling = DimensionTiling.forLevel(actor.level());
        double followRange = actor.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (tiling.tileSizeBlocks() >= followRange) {
            return Set.of(nearestAliasBlockPos(actor, target));
        }
        return aliasBlockPositions(actor, target, ENTITY_TARGET_ALIAS_RADIUS);
    }

    public static Set<BlockPos> pathTargetBlockPositions(Mob actor, BlockPos target) {
        if (!enabled(actor.level())) {
            return Set.of(target);
        }

        TopologyContext topology = TopologyContexts.forLevel(actor.level());
        BlockPos canonical = topology.canonicalBlock(target);
        BlockPos nearest = topology.virtualBlockForViewer(canonical, actor.getX(), actor.getZ());
        double followRange = actor.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (topology.tileSizeBlocks() >= followRange) {
            return Set.of(nearest);
        }

        Set<BlockPos> positions = new LinkedHashSet<>();
        AABB unitBox = AABB.ofSize(nearest.getCenter(), 1.0D, 1.0D, 1.0D);
        for (AABB aliasBox : TileGeometry.create(topology.tiling()).nearbyAliasBoxes(unitBox, actor.position(), ENTITY_TARGET_ALIAS_RADIUS)) {
            positions.add(BlockPos.containing(aliasBox.getCenter()));
        }
        return positions;
    }

    public static double horizontalDistanceToSqr(Entity actor, Entity target) {
        if (!canAlias(actor, target)) {
            double dx = actor.getX() - target.getX();
            double dz = actor.getZ() - target.getZ();
            return dx * dx + dz * dz;
        }
        TopologyContext topology = TopologyContexts.forLevel(actor.level());
        return topology.wrappedDistanceSqr(
                new Vec3(actor.getX(), 0.0D, actor.getZ()),
                new Vec3(target.getX(), 0.0D, target.getZ()));
    }

    public static AABB canonicalQueryBox(Level level, AABB rawBox) {
        if (!enabled(level)) {
            return rawBox;
        }
        return TopologyContexts.forLevel(level).canonicalBox(rawBox);
    }

    public static AABB nearestAliasQueryBox(Entity actor, AABB rawBox) {
        if (!enabled(actor.level())) {
            return rawBox;
        }
        TopologyContext context = TopologyContexts.forLevel(actor.level());
        return context.virtualBoxForViewer(canonicalQueryBox(actor.level(), rawBox), actor.position());
    }

    public static AABB nearestAliasBoundingBox(Entity actor, Entity target) {
        if (!canAlias(actor, target)) {
            return target.getBoundingBox();
        }
        return nearestAliasQueryBox(actor, target.getBoundingBox());
    }

    public static AABB nearestAliasHitbox(Entity actor, LivingEntity target, AABB rawHitbox) {
        if (!canAlias(actor, target)) {
            return rawHitbox;
        }
        return nearestAliasQueryBox(actor, rawHitbox);
    }

    public static boolean aliasLineOfSight(LivingEntity actor, Entity target) {
        if (!canAlias(actor, target)) {
            return false;
        }

        return TopologicalRaycasts.topologicalLineOfSight(actor, target);
    }

    public static boolean wrappedHorizontalDistanceIsShorter(Entity actor, Entity target) {
        if (!canAlias(actor, target)) {
            return false;
        }

        double rawDx = actor.getX() - target.getX();
        double rawDz = actor.getZ() - target.getZ();
        double rawHorizontalSqr = rawDx * rawDx + rawDz * rawDz;
        return horizontalDistanceToSqr(actor, target) < rawHorizontalSqr;
    }
}
