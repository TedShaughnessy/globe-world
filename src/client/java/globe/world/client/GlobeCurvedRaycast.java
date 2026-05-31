package globe.world.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class GlobeCurvedRaycast {
    private static final double SEGMENT_LENGTH_BLOCKS = 0.125D;
    private static final int MIN_SEGMENTS = 8;
    private static final int MAX_SEGMENTS = 128;

    private GlobeCurvedRaycast() {
    }

    public static HitResult pickBlock(Entity cameraEntity, double range, float partialTicks, boolean withLiquids) {
        if (GlobeCurvatureShader.curvatureRadius() <= 0.0D) {
            return cameraEntity.pick(range, partialTicks, withLiquids);
        }

        Vec3 from = cameraEntity.getEyePosition(partialTicks);
        Vec3 viewVector = cameraEntity.getViewVector(partialTicks);
        Level level = cameraEntity.level();
        ClipContext.Fluid fluid = withLiquids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE;
        int segments = Math.clamp((int) Math.ceil(range / SEGMENT_LENGTH_BLOCKS), MIN_SEGMENTS, MAX_SEGMENTS);
        Vec3 previous = from;

        for (int segment = 1; segment <= segments; segment++) {
            double distance = range * segment / segments;
            Vec3 current = globeWorld$curvedWorldPoint(from, viewVector, distance);
            BlockHitResult hit = level.clip(new ClipContext(previous, current, ClipContext.Block.OUTLINE, fluid, cameraEntity));
            if (hit.getType() != HitResult.Type.MISS) {
                return hit;
            }
            previous = current;
        }

        Vec3 delta = from.subtract(previous);
        return BlockHitResult.miss(previous, Direction.getApproximateNearest(delta.x, delta.y, delta.z), BlockPos.containing(previous));
    }

    private static Vec3 globeWorld$curvedWorldPoint(Vec3 from, Vec3 viewVector, double distance) {
        double x = viewVector.x * distance;
        double z = viewVector.z * distance;
        double drop = GlobeCurvatureShader.curvatureDrop(x * x + z * z);
        return from.add(x, viewVector.y * distance + drop, z);
    }
}
