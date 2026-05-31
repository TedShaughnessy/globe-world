package globe.world.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class GlobeCurvedRaycast {
    private static final double SEGMENT_LENGTH_BLOCKS = 0.125D;
    private static final int MIN_SEGMENTS = 8;
    private static final int MAX_SEGMENTS = 128;

    private GlobeCurvedRaycast() {
    }

    public static HitResult pick(Entity cameraEntity, double blockInteractionRange, double entityInteractionRange, float partialTicks) {
        double maxDistance = Math.max(blockInteractionRange, entityInteractionRange);
        BlockPick blockPick = pickBlockWithDistance(cameraEntity.level(), cameraEntity, maxDistance, partialTicks, ClipContext.Fluid.NONE);
        double entityLimit = blockPick.hit().getType() == HitResult.Type.MISS
                ? maxDistance
                : Math.min(maxDistance, blockPick.distance());
        EntityPick entityPick = pickEntity(cameraEntity, entityLimit, partialTicks);
        if (entityPick != null && entityPick.distance() <= entityInteractionRange && entityPick.distance() < blockPick.distance()) {
            return entityPick.hit();
        }
        if (blockPick.distance() <= blockInteractionRange) {
            return blockPick.hit();
        }

        Vec3 location = blockPick.hit().getLocation();
        Direction direction = Direction.getApproximateNearest(
                location.x - cameraEntity.getEyePosition(partialTicks).x,
                location.y - cameraEntity.getEyePosition(partialTicks).y,
                location.z - cameraEntity.getEyePosition(partialTicks).z
        );
        return BlockHitResult.miss(location, direction, BlockPos.containing(location));
    }

    public static BlockHitResult pickBlock(Entity cameraEntity, double range, float partialTicks, boolean withLiquids) {
        return pickBlock(cameraEntity, range, partialTicks, withLiquids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE);
    }

    public static BlockHitResult pickBlock(Entity cameraEntity, double range, float partialTicks, ClipContext.Fluid fluid) {
        return pickBlock(cameraEntity.level(), cameraEntity, range, partialTicks, fluid);
    }

    public static BlockHitResult pickBlock(Level level, Entity cameraEntity, double range, float partialTicks, ClipContext.Fluid fluid) {
        return pickBlockWithDistance(level, cameraEntity, range, partialTicks, fluid).hit();
    }

    private static BlockPick pickBlockWithDistance(Level level, Entity cameraEntity, double range, float partialTicks, ClipContext.Fluid fluid) {
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        double curvatureRadius = GlobeCurvature.curvatureRadius(tiling, level.dimension());
        Vec3 from = cameraEntity.getEyePosition(partialTicks);
        Vec3 viewVector = cameraEntity.getViewVector(partialTicks);

        if (curvatureRadius <= 0.0D) {
            BlockHitResult hit = pickStraight(level, cameraEntity, from, viewVector, range, fluid);
            return new BlockPick(hit, from.distanceTo(hit.getLocation()));
        }

        int segments = Math.clamp((int) Math.ceil(range / SEGMENT_LENGTH_BLOCKS), MIN_SEGMENTS, MAX_SEGMENTS);
        Vec3 previous = from;
        double previousDistance = 0.0D;

        for (int segment = 1; segment <= segments; segment++) {
            double distance = range * segment / segments;
            Vec3 current = curvedWorldPoint(from, viewVector, distance, curvatureRadius, tiling);
            BlockHitResult hit = level.clip(new ClipContext(previous, current, ClipContext.Block.OUTLINE, fluid, cameraEntity));
            if (hit.getType() != HitResult.Type.MISS) {
                return new BlockPick(hit, hitDistance(previous, current, previousDistance, distance, hit.getLocation()));
            }
            previous = current;
            previousDistance = distance;
        }

        Vec3 delta = from.subtract(previous);
        return new BlockPick(
                BlockHitResult.miss(previous, Direction.getApproximateNearest(delta.x, delta.y, delta.z), BlockPos.containing(previous)),
                range
        );
    }

    private static BlockHitResult pickStraight(Level level, Entity cameraEntity, Vec3 from, Vec3 viewVector, double range, ClipContext.Fluid fluid) {
        Vec3 to = from.add(viewVector.x * range, viewVector.y * range, viewVector.z * range);
        return level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, fluid, cameraEntity));
    }

    private static Vec3 curvedWorldPoint(Vec3 from, Vec3 viewVector, double distance, double curvatureRadius, DimensionTiling tiling) {
        double x = viewVector.x * distance;
        double z = viewVector.z * distance;
        double drop = GlobeCurvature.curvatureDrop(curvatureRadius, tiling, x * x + z * z);
        return from.add(x, viewVector.y * distance + drop, z);
    }

    private static EntityPick pickEntity(Entity cameraEntity, double range, float partialTicks) {
        Level level = cameraEntity.level();
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        double curvatureRadius = GlobeCurvature.curvatureRadius(tiling, level.dimension());
        Vec3 from = cameraEntity.getEyePosition(partialTicks);
        Vec3 viewVector = cameraEntity.getViewVector(partialTicks);
        int segments = curvatureRadius <= 0.0D ? 1 : Math.clamp((int) Math.ceil(range / SEGMENT_LENGTH_BLOCKS), MIN_SEGMENTS, MAX_SEGMENTS);
        Vec3[] points = new Vec3[segments + 1];
        points[0] = from;
        AABB searchArea = new AABB(from, from);

        for (int segment = 1; segment <= segments; segment++) {
            double distance = range * segment / segments;
            Vec3 point = curvatureRadius <= 0.0D
                    ? from.add(viewVector.x * distance, viewVector.y * distance, viewVector.z * distance)
                    : curvedWorldPoint(from, viewVector, distance, curvatureRadius, tiling);
            points[segment] = point;
            searchArea = searchArea.minmax(new AABB(points[segment - 1], point));
        }

        Entity nearestEntity = null;
        Vec3 nearestLocation = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Entity entity : level.getEntities(cameraEntity, searchArea.inflate(1.0D), EntitySelector.CAN_BE_PICKED)) {
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            for (int segment = 1; segment <= segments; segment++) {
                Vec3 previous = points[segment - 1];
                Vec3 current = points[segment];
                double previousDistance = range * (segment - 1) / segments;
                double currentDistance = range * segment / segments;
                Optional<Vec3> clipPoint = box.clip(previous, current);
                Vec3 location = null;
                double distance = Double.MAX_VALUE;

                if (box.contains(previous)) {
                    location = previous;
                    distance = previousDistance;
                } else if (clipPoint.isPresent()) {
                    location = clipPoint.get();
                    distance = hitDistance(previous, current, previousDistance, currentDistance, location);
                }

                if (location != null && distance < nearestDistance) {
                    if (entity.getRootVehicle() == cameraEntity.getRootVehicle() && distance > 0.0D) {
                        continue;
                    }
                    nearestEntity = entity;
                    nearestLocation = location;
                    nearestDistance = distance;
                }
            }
        }

        return nearestEntity == null ? null : new EntityPick(new EntityHitResult(nearestEntity, nearestLocation), nearestDistance);
    }

    private static double hitDistance(Vec3 from, Vec3 to, double fromDistance, double toDistance, Vec3 hit) {
        double segmentLengthSqr = from.distanceToSqr(to);
        if (segmentLengthSqr <= 1.0E-12D) {
            return fromDistance;
        }
        double hitLength = Math.sqrt(from.distanceToSqr(hit));
        double segmentLength = Math.sqrt(segmentLengthSqr);
        double progress = Math.clamp(hitLength / segmentLength, 0.0D, 1.0D);
        return fromDistance + (toDistance - fromDistance) * progress;
    }

    private record BlockPick(BlockHitResult hit, double distance) {
    }

    private record EntityPick(EntityHitResult hit, double distance) {
    }
}
