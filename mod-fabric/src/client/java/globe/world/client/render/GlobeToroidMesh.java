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

final class GlobeToroidMesh {
    static final SpriteId BLANK_TEXTURE = new SpriteId(
            TextureAtlas.LOCATION_BLOCKS,
            Identifier.withDefaultNamespace("block/calcite"));
    private static final float FULL_TURN_RADIANS = (float)(Math.PI * 2.0D);
    private static final int MAJOR_SEGMENTS = 64;
    private static final int MINOR_SEGMENTS = 24;
    private static final float CENTER = 0.5F;
    private static final float MAJOR_RADIUS = 0.265F;
    private static final float MINOR_RADIUS = 0.155F;
    private static final float COMPARISON_SCALE = 0.48F;
    private static final float COMPARISON_LOW = 0.27F;
    private static final float COMPARISON_HIGH = 0.73F;
    private static final float HOLOGRAM_CENTER_Y = 2.0F;
    private static final float HOLOGRAM_SCALE = 8.0F;
    private static final int COLOR = -1;

    private GlobeToroidMesh() {
    }

    static RenderType renderType() {
        return RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS);
    }

    static RenderType renderType(final Identifier texture) {
        return RenderTypes.entitySolid(texture);
    }

    static RenderType translucentRenderType(final Identifier texture) {
        return RenderTypes.entityTranslucent(texture);
    }

    static void submit(
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final TextureAtlasSprite sprite,
            final int lightCoords,
            final int overlayCoords) {
        submitNodeCollector.submitCustomGeometry(poseStack, renderType(), (pose, buffer) ->
                renderTorus(pose, buffer, sprite, lightCoords, overlayCoords));
    }

    static void submitComparison(
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final Identifier texture,
            final int lightCoords,
            final int overlayCoords) {
        submitComparisonVariant(
                poseStack,
                submitNodeCollector,
                texture,
                COMPARISON_LOW,
                COMPARISON_LOW,
                TextureMapping.X_MAJOR_Z_MINOR,
                SurfaceSide.OUTSIDE,
                lightCoords,
                overlayCoords);
        submitComparisonVariant(
                poseStack,
                submitNodeCollector,
                texture,
                COMPARISON_HIGH,
                COMPARISON_LOW,
                TextureMapping.X_MAJOR_Z_MINOR,
                SurfaceSide.INSIDE,
                lightCoords,
                overlayCoords);
        submitComparisonVariant(
                poseStack,
                submitNodeCollector,
                texture,
                COMPARISON_LOW,
                COMPARISON_HIGH,
                TextureMapping.Z_MAJOR_X_MINOR,
                SurfaceSide.OUTSIDE,
                lightCoords,
                overlayCoords);
        submitComparisonVariant(
                poseStack,
                submitNodeCollector,
                texture,
                COMPARISON_HIGH,
                COMPARISON_HIGH,
                TextureMapping.Z_MAJOR_X_MINOR,
                SurfaceSide.INSIDE,
                lightCoords,
                overlayCoords);
    }

    static void submitHologram(
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final Identifier texture,
            final TextureMapping mapping,
            final int color,
            final int lightCoords,
            final int overlayCoords) {
        submitHologramVariant(
                poseStack,
                submitNodeCollector,
                texture,
                mapping,
                SurfaceSide.OUTSIDE,
                color,
                lightCoords,
                overlayCoords);
    }

    private static void renderTorus(
            final PoseStack.Pose pose,
            final VertexConsumer buffer,
            final TextureAtlasSprite sprite,
            final int lightCoords,
            final int overlayCoords) {
        for (int major = 0; major < MAJOR_SEGMENTS; major++) {
            float u0 = major / (float)MAJOR_SEGMENTS;
            float u1 = (major + 1) / (float)MAJOR_SEGMENTS;
            for (int minor = 0; minor < MINOR_SEGMENTS; minor++) {
                float v0 = minor / (float)MINOR_SEGMENTS;
                float v1 = (minor + 1) / (float)MINOR_SEGMENTS;
                spriteVertex(buffer, pose, sprite, u0, v0, CENTER, CENTER, CENTER, 1.0F, lightCoords, overlayCoords);
                spriteVertex(buffer, pose, sprite, u1, v0, CENTER, CENTER, CENTER, 1.0F, lightCoords, overlayCoords);
                spriteVertex(buffer, pose, sprite, u1, v1, CENTER, CENTER, CENTER, 1.0F, lightCoords, overlayCoords);
                spriteVertex(buffer, pose, sprite, u0, v1, CENTER, CENTER, CENTER, 1.0F, lightCoords, overlayCoords);
            }
        }
    }

    private static void renderMappedTorus(
            final PoseStack.Pose pose,
            final VertexConsumer buffer,
            final TextureMapping mapping,
            final SurfaceSide side,
            final float centerX,
            final float centerY,
            final float centerZ,
            final float scale,
            final int color,
            final int lightCoords,
            final int overlayCoords) {
        for (int major = 0; major < MAJOR_SEGMENTS; major++) {
            float u0 = major / (float)MAJOR_SEGMENTS;
            float u1 = (major + 1) / (float)MAJOR_SEGMENTS;
            for (int minor = 0; minor < MINOR_SEGMENTS; minor++) {
                float v0 = minor / (float)MINOR_SEGMENTS;
                float v1 = (minor + 1) / (float)MINOR_SEGMENTS;
                if (side == SurfaceSide.OUTSIDE) {
                    mappedVertex(buffer, pose, u0, v0, mapping, side, centerX, centerY, centerZ, scale, color, lightCoords, overlayCoords);
                    mappedVertex(buffer, pose, u0, v1, mapping, side, centerX, centerY, centerZ, scale, color, lightCoords, overlayCoords);
                    mappedVertex(buffer, pose, u1, v1, mapping, side, centerX, centerY, centerZ, scale, color, lightCoords, overlayCoords);
                    mappedVertex(buffer, pose, u1, v0, mapping, side, centerX, centerY, centerZ, scale, color, lightCoords, overlayCoords);
                } else {
                    mappedVertex(buffer, pose, u0, v0, mapping, side, centerX, centerY, centerZ, scale, color, lightCoords, overlayCoords);
                    mappedVertex(buffer, pose, u1, v0, mapping, side, centerX, centerY, centerZ, scale, color, lightCoords, overlayCoords);
                    mappedVertex(buffer, pose, u1, v1, mapping, side, centerX, centerY, centerZ, scale, color, lightCoords, overlayCoords);
                    mappedVertex(buffer, pose, u0, v1, mapping, side, centerX, centerY, centerZ, scale, color, lightCoords, overlayCoords);
                }
            }
        }
    }

    private static void submitComparisonVariant(
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final Identifier texture,
            final float centerX,
            final float centerZ,
            final TextureMapping mapping,
            final SurfaceSide side,
            final int lightCoords,
            final int overlayCoords) {
        submitNodeCollector.submitCustomGeometry(poseStack, renderType(texture), (pose, buffer) ->
                renderMappedTorus(
                        pose,
                        buffer,
                        mapping,
                        side,
                        centerX,
                        CENTER,
                        centerZ,
                        COMPARISON_SCALE,
                        COLOR,
                        lightCoords,
                        overlayCoords));
    }

    private static void submitHologramVariant(
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final Identifier texture,
            final TextureMapping mapping,
            final SurfaceSide side,
            final int color,
            final int lightCoords,
            final int overlayCoords) {
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                translucentRenderType(texture),
                (pose, buffer) -> renderMappedTorus(
                        pose,
                        buffer,
                        mapping,
                        side,
                        CENTER,
                        HOLOGRAM_CENTER_Y,
                        CENTER,
                        HOLOGRAM_SCALE,
                        color,
                        lightCoords,
                        overlayCoords));
    }

    private static void spriteVertex(
            final VertexConsumer buffer,
            final PoseStack.Pose pose,
            final TextureAtlasSprite sprite,
            final float u,
            final float v,
            final float centerX,
            final float centerY,
            final float centerZ,
            final float scale,
            final int lightCoords,
            final int overlayCoords) {
        torusVertex(
                buffer,
                pose,
                u,
                v,
                sprite.getU(u),
                sprite.getV(v),
                centerX,
                centerY,
                centerZ,
                scale,
                COLOR,
                1.0F,
                lightCoords,
                overlayCoords);
    }

    private static void mappedVertex(
            final VertexConsumer buffer,
            final PoseStack.Pose pose,
            final float u,
            final float v,
            final TextureMapping mapping,
            final SurfaceSide side,
            final float centerX,
            final float centerY,
            final float centerZ,
            final float scale,
            final int color,
            final int lightCoords,
            final int overlayCoords) {
        torusVertex(
                buffer,
                pose,
                u,
                v,
                mapping.textureU(u, v),
                mapping.textureV(u, v),
                centerX,
                centerY,
                centerZ,
                scale,
                color,
                side.normalScale,
                lightCoords,
                overlayCoords);
    }

    private static void torusVertex(
            final VertexConsumer buffer,
            final PoseStack.Pose pose,
            final float u,
            final float v,
            final float textureU,
            final float textureV,
            final float centerX,
            final float centerY,
            final float centerZ,
            final float scale,
            final int color,
            final float normalScale,
            final int lightCoords,
            final int overlayCoords) {
        float majorAngle = FULL_TURN_RADIANS * u - (float)(Math.PI * 0.5D);
        float minorAngle = FULL_TURN_RADIANS * v;
        float majorCos = (float)Math.cos(majorAngle);
        float majorSin = (float)Math.sin(majorAngle);
        float minorCos = (float)Math.cos(minorAngle);
        float minorSin = (float)Math.sin(minorAngle);
        float ringRadius = MAJOR_RADIUS + MINOR_RADIUS * minorCos;
        float normalX = majorCos * minorCos;
        float normalY = minorSin;
        float normalZ = majorSin * minorCos;
        float x = CENTER + majorCos * ringRadius;
        float y = CENTER + MINOR_RADIUS * minorSin;
        float z = CENTER + majorSin * ringRadius;
        buffer.addVertex(
                        pose.pose(),
                        centerX + (x - CENTER) * scale,
                        centerY + (y - CENTER) * scale,
                        centerZ + (z - CENTER) * scale)
                .setColor(color)
                .setUv(textureU, textureV)
                .setOverlay(overlayCoords)
                .setLight(lightCoords)
                .setNormal(pose, normalX * normalScale, normalY * normalScale, normalZ * normalScale);
    }

    enum TextureMapping {
        X_MAJOR_Z_MINOR {
            @Override
            float textureU(final float major, final float minor) {
                return major;
            }

            @Override
            float textureV(final float major, final float minor) {
                return minor;
            }
        },
        Z_MAJOR_X_MINOR {
            @Override
            float textureU(final float major, final float minor) {
                return minor;
            }

            @Override
            float textureV(final float major, final float minor) {
                return major;
            }
        };

        abstract float textureU(float major, float minor);

        abstract float textureV(float major, float minor);
    }

    enum SurfaceSide {
        OUTSIDE(1.0F),
        INSIDE(-1.0F);

        final float normalScale;

        SurfaceSide(final float normalScale) {
            this.normalScale = normalScale;
        }
    }
}
