package globe.world.util;

import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

public final class AiAliasUtil {
    private static final int ENTITY_TARGET_ALIAS_RADIUS = 1;

    private AiAliasUtil() {
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
        Vec3 alias = nearestAliasPosition(
                actor.level(),
                actor.getX(),
                actor.getY(),
                actor.getZ(),
                target.getX(),
                target.getEyeY(),
                target.getZ());
        return alias;
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
        Vec3 canonical = new Vec3(topology.canonicalBlockX(targetX), targetY, topology.canonicalBlockX(targetZ));
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

        int tileSize = tiling.tileSizeBlocks();
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (int tileX = -tileRadius; tileX <= tileRadius; tileX++) {
            for (int tileZ = -tileRadius; tileZ <= tileRadius; tileZ++) {
                positions.add(center.offset(tileX * tileSize, 0, tileZ * tileSize));
            }
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

    public static double horizontalDistanceToSqr(Entity actor, Entity target) {
        if (!canAlias(actor, target)) {
            double dx = actor.getX() - target.getX();
            double dz = actor.getZ() - target.getZ();
            return dx * dx + dz * dz;
        }
        return CoordUtil.wrappedDistanceSqrXZ(actor.level(), actor.getX(), actor.getZ(), target.getX(), target.getZ());
    }

    public static AABB canonicalQueryBox(Level level, AABB rawBox) {
        if (!enabled(level)) {
            return rawBox;
        }
        return CoordUtil.wrapAabb(level, rawBox);
    }

    public static AABB nearestAliasQueryBox(Entity actor, AABB rawBox) {
        if (!enabled(actor.level())) {
            return rawBox;
        }
        return CoordUtil.virtualAabb(actor.level(), canonicalQueryBox(actor.level(), rawBox), actor.getX(), actor.getZ());
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

        Vec3 from = new Vec3(actor.getX(), actor.getEyeY(), actor.getZ());
        Vec3 to = nearestAliasEyePosition(actor, target);
        if (to.distanceTo(from) > 128.0D) {
            return false;
        }
        return actor.level()
                .clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor))
                .getType() == HitResult.Type.MISS;
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
