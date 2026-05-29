package globe.world.client;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;

public final class GlobeSkyHorizon {
    private static final double VANILLA_CELESTIAL_RADIUS = 100.0D;
    private static final double SKY_DISC_RADIUS = 512.0D;
    private static final double CELESTIAL_RADIUS = SKY_DISC_RADIUS;
    private static final double HORIZON_EFFECT_RADIUS = 100.0D;
    private static final double MAX_DOWNWARD_SLOPE = 1.5D;
    private static float skyDiscYOffset;
    private static float horizonEffectYOffset;

    private GlobeSkyHorizon() {
    }

    public static void update(ClientLevel level, Camera camera) {
        double curvatureRadius = GlobeCurvatureShader.curvatureRadius();
        if (curvatureRadius <= 0.0D) {
            reset();
            return;
        }

        double heightAboveHorizon = camera.position().y - level.getLevelData().getHorizonHeight(level);
        if (heightAboveHorizon <= 0.0D) {
            reset();
            return;
        }

        // Tangent slope for y = -distance^2 / (2 * radius) from the camera height above the horizon plane.
        double downwardSlope = -Math.sqrt(2.0D * heightAboveHorizon / curvatureRadius);
        downwardSlope = Math.clamp(downwardSlope, -MAX_DOWNWARD_SLOPE, 0.0D);
        skyDiscYOffset = (float) (SKY_DISC_RADIUS * downwardSlope);
        horizonEffectYOffset = (float) (HORIZON_EFFECT_RADIUS * downwardSlope);
    }

    public static float skyDiscYOffset() {
        return skyDiscYOffset;
    }

    public static float horizonEffectYOffset() {
        return horizonEffectYOffset;
    }

    public static float celestialRadius() {
        return (float) CELESTIAL_RADIUS;
    }

    public static float celestialScale() {
        return (float) (CELESTIAL_RADIUS / VANILLA_CELESTIAL_RADIUS);
    }

    private static void reset() {
        skyDiscYOffset = 0.0F;
        horizonEffectYOffset = 0.0F;
    }
}
