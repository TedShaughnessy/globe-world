package globe.world.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;

final class GlobeSphereMesh {
    static final SpriteId BLANK_TEXTURE = new SpriteId(TextureAtlas.LOCATION_BLOCKS, Identifier.withDefaultNamespace("block/calcite"));
    private static final int SEGMENTS = 48;
    private static final int RINGS = 24;
    private static final float CENTER = 0.5F;
    private static final float RADIUS = 0.43F;
    private static final float FULL_TURN_RADIANS = (float)(Math.PI * 2.0D);
    private static final int COLOR = -1;

    private GlobeSphereMesh() {
    }

    static RenderType renderType() {
        return RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS);
    }

    static RenderType renderType(final Identifier texture) {
        return RenderTypes.entitySolid(texture);
    }

    static void submit(
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final TextureAtlasSprite sprite,
            final int lightCoords,
            final int overlayCoords) {
        submitNodeCollector.submitCustomGeometry(poseStack, renderType(), (pose, buffer) ->
                render(pose, buffer, sprite, lightCoords, overlayCoords));
    }

    static void submitProjected(
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final Identifier texture,
            final float centerU,
            final float centerV,
            final int lightCoords,
            final int overlayCoords) {
        submitNodeCollector.submitCustomGeometry(poseStack, renderType(texture), (pose, buffer) ->
                renderProjected(pose, buffer, centerU, centerV, lightCoords, overlayCoords));
    }

    static void render(
            final PoseStack.Pose pose,
            final VertexConsumer buffer,
            final TextureAtlasSprite sprite,
            final int lightCoords,
            final int overlayCoords) {
        for (int ring = 0; ring < RINGS; ring++) {
            float v0 = ring / (float)RINGS;
            float v1 = (ring + 1) / (float)RINGS;
            float theta0 = (float)Math.PI * v0;
            float theta1 = (float)Math.PI * v1;

            for (int segment = 0; segment < SEGMENTS; segment++) {
                float u0 = segment / (float)SEGMENTS;
                float u1 = (segment + 1) / (float)SEGMENTS;
                float phi0 = FULL_TURN_RADIANS * u0;
                float phi1 = FULL_TURN_RADIANS * u1;

                vertex(buffer, pose, sprite, theta0, phi0, u0, v0, lightCoords, overlayCoords);
                vertex(buffer, pose, sprite, theta0, phi1, u1, v0, lightCoords, overlayCoords);
                vertex(buffer, pose, sprite, theta1, phi1, u1, v1, lightCoords, overlayCoords);
                vertex(buffer, pose, sprite, theta1, phi0, u0, v1, lightCoords, overlayCoords);
            }
        }
    }

    static void renderProjected(
            final PoseStack.Pose pose,
            final VertexConsumer buffer,
            final float centerU,
            final float centerV,
            final int lightCoords,
            final int overlayCoords) {
        for (int ring = 0; ring < RINGS; ring++) {
            float v0 = ring / (float)RINGS;
            float v1 = (ring + 1) / (float)RINGS;
            float theta0 = (float)Math.PI * v0;
            float theta1 = (float)Math.PI * v1;

            for (int segment = 0; segment < SEGMENTS; segment++) {
                float u0 = segment / (float)SEGMENTS;
                float u1 = (segment + 1) / (float)SEGMENTS;
                float phi0 = FULL_TURN_RADIANS * u0;
                float phi1 = FULL_TURN_RADIANS * u1;

                projectedVertex(buffer, pose, theta0, phi0, centerU, centerV, lightCoords, overlayCoords);
                projectedVertex(buffer, pose, theta0, phi1, centerU, centerV, lightCoords, overlayCoords);
                projectedVertex(buffer, pose, theta1, phi1, centerU, centerV, lightCoords, overlayCoords);
                projectedVertex(buffer, pose, theta1, phi0, centerU, centerV, lightCoords, overlayCoords);
            }
        }
    }

    private static void vertex(
            final VertexConsumer buffer,
            final PoseStack.Pose pose,
            final TextureAtlasSprite sprite,
            final float theta,
            final float phi,
            final float u,
            final float v,
            final int lightCoords,
            final int overlayCoords) {
        float sinTheta = (float)Math.sin(theta);
        float normalX = sinTheta * (float)Math.cos(phi);
        float normalY = (float)Math.cos(theta);
        float normalZ = sinTheta * (float)Math.sin(phi);
        buffer.addVertex(
                        pose.pose(),
                        CENTER + normalX * RADIUS,
                        CENTER + normalY * RADIUS,
                        CENTER + normalZ * RADIUS)
                .setColor(COLOR)
                .setUv(sprite.getU(u), sprite.getV(v))
                .setOverlay(overlayCoords)
                .setLight(lightCoords)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static void projectedVertex(
            final VertexConsumer buffer,
            final PoseStack.Pose pose,
            final float theta,
            final float phi,
            final float centerU,
            final float centerV,
            final int lightCoords,
            final int overlayCoords) {
        float sinTheta = (float)Math.sin(theta);
        float normalX = sinTheta * (float)Math.cos(phi);
        float normalY = (float)Math.cos(theta);
        float normalZ = sinTheta * (float)Math.sin(phi);
        float frontAngle = (float)Math.acos(clampUnit(-normalZ));
        float radialTileDistance = frontAngle / FULL_TURN_RADIANS;
        float directionLength = (float)Math.sqrt(normalX * normalX + normalY * normalY);
        float mapU = centerU;
        float mapV = centerV;
        if (directionLength > 1.0E-5F) {
            mapU += normalX / directionLength * radialTileDistance;
            mapV -= normalY / directionLength * radialTileDistance;
        }
        buffer.addVertex(
                        pose.pose(),
                        CENTER + normalX * RADIUS,
                        CENTER + normalY * RADIUS,
                        CENTER + normalZ * RADIUS)
                .setColor(COLOR)
                .setUv(mapU, mapV)
                .setOverlay(overlayCoords)
                .setLight(lightCoords)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static float clampUnit(final float value) {
        return Math.max(-1.0F, Math.min(1.0F, value));
    }
}
