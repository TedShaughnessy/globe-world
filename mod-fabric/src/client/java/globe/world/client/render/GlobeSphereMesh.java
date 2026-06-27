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
    static final float FULL_TURN_RADIANS = (float)(Math.PI * 2.0D);
    private static final int COLOR = -1;
    private static final float DEFAULT_FRONT_X = 0.0F;
    private static final float DEFAULT_FRONT_Y = 0.0F;
    private static final float DEFAULT_FRONT_Z = -1.0F;
    private static final float DEFAULT_U_AXIS_X = 1.0F;
    private static final float DEFAULT_U_AXIS_Y = 0.0F;
    private static final float DEFAULT_U_AXIS_Z = 0.0F;
    private static final float DEFAULT_V_AXIS_X = 0.0F;
    private static final float DEFAULT_V_AXIS_Y = 1.0F;
    private static final float DEFAULT_V_AXIS_Z = 0.0F;
    private static final float DEBUG_MARKER_RADIUS = RADIUS + 0.012F;
    private static final float DEBUG_MARKER_SIZE = 0.035F;
    private static final float DEBUG_ACTIVE_MARKER_SIZE = 0.05F;

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
                renderProjected(
                        pose,
                        buffer,
                        centerU,
                        centerV,
                        DEFAULT_FRONT_X,
                        DEFAULT_FRONT_Y,
                        DEFAULT_FRONT_Z,
                        DEFAULT_U_AXIS_X,
                        DEFAULT_U_AXIS_Y,
                        DEFAULT_U_AXIS_Z,
                        DEFAULT_V_AXIS_X,
                        DEFAULT_V_AXIS_Y,
                        DEFAULT_V_AXIS_Z,
                        lightCoords,
                        overlayCoords));
    }

    static void submitDebugAnchors(
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final TextureAtlasSprite sprite,
            final DebugAnchor[] anchors,
            final int lightCoords,
            final int overlayCoords) {
        submitNodeCollector.submitCustomGeometry(poseStack, renderType(), (pose, buffer) ->
                renderDebugAnchors(pose, buffer, sprite, anchors, lightCoords, overlayCoords));
    }

    static void submitProjected(
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final Identifier texture,
            final float centerU,
            final float centerV,
            final ProjectionBasis basis,
            final int lightCoords,
            final int overlayCoords) {
        submitNodeCollector.submitCustomGeometry(poseStack, renderType(texture), (pose, buffer) ->
                renderProjected(
                        pose,
                        buffer,
                        centerU,
                        centerV,
                        basis.frontX,
                        basis.frontY,
                        basis.frontZ,
                        basis.uAxisX,
                        basis.uAxisY,
                        basis.uAxisZ,
                        basis.vAxisX,
                        basis.vAxisY,
                        basis.vAxisZ,
                        lightCoords,
                        overlayCoords));
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
            final float frontX,
            final float frontY,
            final float frontZ,
            final float uAxisX,
            final float uAxisY,
            final float uAxisZ,
            final float vAxisX,
            final float vAxisY,
            final float vAxisZ,
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

                projectedVertex(
                        buffer,
                        pose,
                        theta0,
                        phi0,
                        centerU,
                        centerV,
                        frontX,
                        frontY,
                        frontZ,
                        uAxisX,
                        uAxisY,
                        uAxisZ,
                        vAxisX,
                        vAxisY,
                        vAxisZ,
                        lightCoords,
                        overlayCoords);
                projectedVertex(
                        buffer,
                        pose,
                        theta0,
                        phi1,
                        centerU,
                        centerV,
                        frontX,
                        frontY,
                        frontZ,
                        uAxisX,
                        uAxisY,
                        uAxisZ,
                        vAxisX,
                        vAxisY,
                        vAxisZ,
                        lightCoords,
                        overlayCoords);
                projectedVertex(
                        buffer,
                        pose,
                        theta1,
                        phi1,
                        centerU,
                        centerV,
                        frontX,
                        frontY,
                        frontZ,
                        uAxisX,
                        uAxisY,
                        uAxisZ,
                        vAxisX,
                        vAxisY,
                        vAxisZ,
                        lightCoords,
                        overlayCoords);
                projectedVertex(
                        buffer,
                        pose,
                        theta1,
                        phi0,
                        centerU,
                        centerV,
                        frontX,
                        frontY,
                        frontZ,
                        uAxisX,
                        uAxisY,
                        uAxisZ,
                        vAxisX,
                        vAxisY,
                        vAxisZ,
                        lightCoords,
                        overlayCoords);
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
            final float frontX,
            final float frontY,
            final float frontZ,
            final float uAxisX,
            final float uAxisY,
            final float uAxisZ,
            final float vAxisX,
            final float vAxisY,
            final float vAxisZ,
            final int lightCoords,
            final int overlayCoords) {
        float sinTheta = (float)Math.sin(theta);
        float normalX = sinTheta * (float)Math.cos(phi);
        float normalY = (float)Math.cos(theta);
        float normalZ = sinTheta * (float)Math.sin(phi);
        float frontDot = clampUnit(normalX * frontX + normalY * frontY + normalZ * frontZ);
        float frontAngle = (float)Math.acos(frontDot);
        float radialTileDistance = frontAngle / FULL_TURN_RADIANS;
        float tangentX = normalX - frontX * frontDot;
        float tangentY = normalY - frontY * frontDot;
        float tangentZ = normalZ - frontZ * frontDot;
        float directionLength = (float)Math.sqrt(tangentX * tangentX + tangentY * tangentY + tangentZ * tangentZ);
        float mapU = centerU;
        float mapV = centerV;
        if (directionLength > 1.0E-5F) {
            float directionX = tangentX / directionLength;
            float directionY = tangentY / directionLength;
            float directionZ = tangentZ / directionLength;
            float uComponent = directionX * uAxisX + directionY * uAxisY + directionZ * uAxisZ;
            float vComponent = directionX * vAxisX + directionY * vAxisY + directionZ * vAxisZ;
            mapU += uComponent * radialTileDistance;
            mapV -= vComponent * radialTileDistance;
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

    private static void renderDebugAnchors(
            final PoseStack.Pose pose,
            final VertexConsumer buffer,
            final TextureAtlasSprite sprite,
            final DebugAnchor[] anchors,
            final int lightCoords,
            final int overlayCoords) {
        for (DebugAnchor anchor : anchors) {
            debugAnchor(buffer, pose, sprite, anchor, lightCoords, overlayCoords);
        }
    }

    private static void debugAnchor(
            final VertexConsumer buffer,
            final PoseStack.Pose pose,
            final TextureAtlasSprite sprite,
            final DebugAnchor anchor,
            final int lightCoords,
            final int overlayCoords) {
        float size = anchor.active ? DEBUG_ACTIVE_MARKER_SIZE : DEBUG_MARKER_SIZE;
        float normalX = anchor.normalX;
        float normalY = anchor.normalY;
        float normalZ = anchor.normalZ;
        float referenceX = Math.abs(normalY) > 0.95F ? 0.0F : 0.0F;
        float referenceY = Math.abs(normalY) > 0.95F ? 0.0F : 1.0F;
        float referenceZ = Math.abs(normalY) > 0.95F ? -1.0F : 0.0F;
        float uAxisX = referenceY * normalZ - referenceZ * normalY;
        float uAxisY = referenceZ * normalX - referenceX * normalZ;
        float uAxisZ = referenceX * normalY - referenceY * normalX;
        float uLength = (float)Math.sqrt(uAxisX * uAxisX + uAxisY * uAxisY + uAxisZ * uAxisZ);
        if (uLength <= 1.0E-5F) {
            return;
        }
        uAxisX /= uLength;
        uAxisY /= uLength;
        uAxisZ /= uLength;
        float vAxisX = normalY * uAxisZ - normalZ * uAxisY;
        float vAxisY = normalZ * uAxisX - normalX * uAxisZ;
        float vAxisZ = normalX * uAxisY - normalY * uAxisX;
        float centerX = CENTER + normalX * DEBUG_MARKER_RADIUS;
        float centerY = CENTER + normalY * DEBUG_MARKER_RADIUS;
        float centerZ = CENTER + normalZ * DEBUG_MARKER_RADIUS;
        float u = sprite.getU(0.5F);
        float v = sprite.getV(0.5F);
        debugVertex(buffer, pose, centerX - uAxisX * size - vAxisX * size, centerY - uAxisY * size - vAxisY * size, centerZ - uAxisZ * size - vAxisZ * size, normalX, normalY, normalZ, anchor.color, u, v, lightCoords, overlayCoords);
        debugVertex(buffer, pose, centerX + uAxisX * size - vAxisX * size, centerY + uAxisY * size - vAxisY * size, centerZ + uAxisZ * size - vAxisZ * size, normalX, normalY, normalZ, anchor.color, u, v, lightCoords, overlayCoords);
        debugVertex(buffer, pose, centerX + uAxisX * size + vAxisX * size, centerY + uAxisY * size + vAxisY * size, centerZ + uAxisZ * size + vAxisZ * size, normalX, normalY, normalZ, anchor.color, u, v, lightCoords, overlayCoords);
        debugVertex(buffer, pose, centerX - uAxisX * size + vAxisX * size, centerY - uAxisY * size + vAxisY * size, centerZ - uAxisZ * size + vAxisZ * size, normalX, normalY, normalZ, anchor.color, u, v, lightCoords, overlayCoords);
    }

    private static void debugVertex(
            final VertexConsumer buffer,
            final PoseStack.Pose pose,
            final float x,
            final float y,
            final float z,
            final float normalX,
            final float normalY,
            final float normalZ,
            final int color,
            final float u,
            final float v,
            final int lightCoords,
            final int overlayCoords) {
        buffer.addVertex(pose.pose(), x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(overlayCoords)
                .setLight(lightCoords)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static float clampUnit(final float value) {
        return Math.max(-1.0F, Math.min(1.0F, value));
    }

    static final class ProjectionBasis {
        final float frontX;
        final float frontY;
        final float frontZ;
        final float uAxisX;
        final float uAxisY;
        final float uAxisZ;
        final float vAxisX;
        final float vAxisY;
        final float vAxisZ;

        ProjectionBasis(
                final float frontX,
                final float frontY,
                final float frontZ,
                final float uAxisX,
                final float uAxisY,
                final float uAxisZ,
                final float vAxisX,
                final float vAxisY,
                final float vAxisZ) {
            this.frontX = frontX;
            this.frontY = frontY;
            this.frontZ = frontZ;
            this.uAxisX = uAxisX;
            this.uAxisY = uAxisY;
            this.uAxisZ = uAxisZ;
            this.vAxisX = vAxisX;
            this.vAxisY = vAxisY;
            this.vAxisZ = vAxisZ;
        }
    }

    static final class DebugAnchor {
        final float normalX;
        final float normalY;
        final float normalZ;
        final int color;
        final boolean active;

        DebugAnchor(final float normalX, final float normalY, final float normalZ, final int color, final boolean active) {
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
            this.color = color;
            this.active = active;
        }
    }
}
