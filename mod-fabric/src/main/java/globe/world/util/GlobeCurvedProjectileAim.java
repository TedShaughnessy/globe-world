package globe.world.util;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class GlobeCurvedProjectileAim {
    public static final double DEFAULT_PLAYER_PROJECTILE_FOCUS_DISTANCE = 15.0D;
    public static final double CLOSE_PLAYER_PROJECTILE_FOCUS_RANGE = 20.0D;

    private GlobeCurvedProjectileAim() {
    }

    public static boolean shootFromVisualAim(
            Projectile projectile,
            Entity source,
            float xRot,
            float yRot,
            float yOffset,
            float power,
            float uncertainty,
            double defaultFocusDistance,
            double closeFocusRange,
            float partialTicks) {
        Vec3 direction = launchDirectionFromVisualAim(source, xRot, yRot, yOffset, defaultFocusDistance, closeFocusRange, partialTicks);
        if (direction == null) {
            return false;
        }

        projectile.shoot(direction.x, direction.y, direction.z, power, uncertainty);
        Vec3 sourceMovement = source.getKnownMovement();
        projectile.setDeltaMovement(projectile.getDeltaMovement().add(sourceMovement.x, source.onGround() ? 0.0D : sourceMovement.y, sourceMovement.z));
        return true;
    }

    public static boolean shootFromVisualDirection(
            Projectile projectile,
            Entity source,
            Vec3 vanillaDirection,
            float power,
            float uncertainty,
            double defaultFocusDistance,
            double closeFocusRange,
            float partialTicks) {
        Vec3 direction = launchDirectionFromVisualDirection(source, vanillaDirection, defaultFocusDistance, closeFocusRange, partialTicks);
        if (direction == null) {
            return false;
        }

        projectile.shoot(direction.x, direction.y, direction.z, power, uncertainty);
        return true;
    }

    public static boolean preserveSpeedFromVisualDirection(
            Projectile projectile,
            Entity source,
            Vec3 vanillaMovement,
            double defaultFocusDistance,
            double closeFocusRange,
            float partialTicks) {
        Vec3 direction = launchDirectionFromVisualDirection(source, vanillaMovement, defaultFocusDistance, closeFocusRange, partialTicks);
        if (direction == null) {
            return false;
        }

        Vec3 movement = direction.normalize().scale(vanillaMovement.length());
        projectile.setDeltaMovement(movement);
        double horizontal = movement.horizontalDistance();
        projectile.setYRot((float)(Mth.atan2(movement.x, movement.z) * 180.0F / (float)Math.PI));
        projectile.setXRot((float)(Mth.atan2(movement.y, horizontal) * 180.0F / (float)Math.PI));
        projectile.yRotO = projectile.getYRot();
        projectile.xRotO = projectile.getXRot();
        return true;
    }

    public static Vec3 launchDirectionFromVisualAim(
            Entity source,
            float xRot,
            float yRot,
            float yOffset,
            double defaultFocusDistance,
            double closeFocusRange,
            float partialTicks) {
        DimensionTiling tiling = DimensionTiling.forLevel(source.level());
        double curvatureRadius = GlobeCurvature.curvatureRadius(tiling, source.level().dimension());
        if (curvatureRadius <= 0.0D || defaultFocusDistance <= 0.0D) {
            return null;
        }

        double focusDistance = focusDistance(source, defaultFocusDistance, closeFocusRange, partialTicks);
        return visualAimVector(xRot, yRot, yOffset, focusDistance, curvatureRadius, tiling);
    }

    public static Vec3 launchDirectionFromVisualDirection(
            Entity source,
            Vec3 vanillaDirection,
            double defaultFocusDistance,
            double closeFocusRange,
            float partialTicks) {
        DimensionTiling tiling = DimensionTiling.forLevel(source.level());
        double curvatureRadius = GlobeCurvature.curvatureRadius(tiling, source.level().dimension());
        if (curvatureRadius <= 0.0D || defaultFocusDistance <= 0.0D || vanillaDirection.lengthSqr() <= 1.0E-7D) {
            return null;
        }

        double focusDistance = focusDistance(source, defaultFocusDistance, closeFocusRange, partialTicks);
        return visualAimVector(vanillaDirection.normalize(), focusDistance, curvatureRadius, tiling);
    }

    private static double focusDistance(Entity source, double defaultFocusDistance, double closeFocusRange, float partialTicks) {
        if (closeFocusRange <= 0.0D) {
            return defaultFocusDistance;
        }

        GlobeCurvedRaycast.Pick pick = GlobeCurvedRaycast.pickWithDistance(
                source,
                closeFocusRange,
                closeFocusRange,
                partialTicks
        );
        if (pick.hit().getType() != HitResult.Type.MISS && pick.distance() <= closeFocusRange) {
            return Math.max(1.0D, pick.distance());
        }
        return defaultFocusDistance;
    }

    private static Vec3 visualAimVector(
            float xRot,
            float yRot,
            float yOffset,
            double focusDistance,
            double curvatureRadius,
            DimensionTiling tiling) {
        float yawRadians = yRot * (float)(Math.PI / 180.0D);
        float pitchRadians = xRot * (float)(Math.PI / 180.0D);
        float offsetPitchRadians = (xRot + yOffset) * (float)(Math.PI / 180.0D);
        double x = -Mth.sin(yawRadians) * Mth.cos(pitchRadians);
        double y = -Mth.sin(offsetPitchRadians);
        double z = Mth.cos(yawRadians) * Mth.cos(pitchRadians);
        double focusedX = x * focusDistance;
        double focusedZ = z * focusDistance;
        double drop = GlobeCurvature.curvatureDrop(curvatureRadius, tiling, focusedX * focusedX + focusedZ * focusedZ);
        return new Vec3(focusedX, y * focusDistance + drop, focusedZ);
    }

    private static Vec3 visualAimVector(
            Vec3 direction,
            double focusDistance,
            double curvatureRadius,
            DimensionTiling tiling) {
        double focusedX = direction.x * focusDistance;
        double focusedZ = direction.z * focusDistance;
        double drop = GlobeCurvature.curvatureDrop(curvatureRadius, tiling, focusedX * focusedX + focusedZ * focusedZ);
        return new Vec3(focusedX, direction.y * focusDistance + drop, focusedZ);
    }
}
