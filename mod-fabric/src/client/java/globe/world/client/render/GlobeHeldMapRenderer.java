package globe.world.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import globe.world.GlobeWorldBlocks;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class GlobeHeldMapRenderer {
    private static final int MIN_VANILLA_MAP_SPAN = 128;
    private static final int MAX_VANILLA_MAP_SPAN = 2048;
    private static final int MAP_SEGMENTS = 24;
    private static final float PROJECTION_SIZE = 1.18F;
    private static final float PROJECTION_HALF_SIZE = PROJECTION_SIZE * 0.5F;
    private static final float SIDE_OFFSET_SCALE = 1.1F;
    private static final int FULL_BRIGHT_LIGHT = 0x00F000F0;
    private static final int MAP_COLOR = 0xE6FFFFFF;

    private GlobeHeldMapRenderer() {
    }

    public static boolean isAtlasProjector(final ItemStack stack) {
        return stack.getItem() == GlobeWorldBlocks.GLOBE_ITEM
                || stack.getItem() == GlobeWorldBlocks.COPPER_ATLAS_PROJECTOR_ITEM
                || stack.getItem() == GlobeWorldBlocks.SOUL_ATLAS_PROJECTOR_ITEM;
    }

    public static void renderAboveAtlas(
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final int lightCoords,
            final HumanoidArm arm,
            final float yawDegrees) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }

        DimensionTiling tiling = DimensionTiling.forLevel(client.level);
        if (!tiling.enabled() || !Level.OVERWORLD.equals(client.level.dimension())) {
            return;
        }

        double centerX = CoordUtil.wrapBlock(tiling, client.player.getX());
        double centerZ = CoordUtil.wrapBlock(tiling, client.player.getZ());
        int mapSpanBlocks = mapSpanBlocks(tiling.tileSizeBlocks());
        Identifier texture = GlobeMapTextureCache.updateHeldViewportForCurrentDimension(
                centerX,
                centerZ,
                yawDegrees,
                mapSpanBlocks,
                tiling.tileSizeBlocks());
        if (texture == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(handOffset(arm), 0.2F, -0.86F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-10.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(10.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(2.0F));

        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucent(texture, false),
                (pose, buffer) -> renderProjection(buffer, pose, FULL_BRIGHT_LIGHT));
        poseStack.popPose();
    }

    private static void renderProjection(
            final VertexConsumer buffer,
            final PoseStack.Pose pose,
            final int lightCoords) {
        for (int xSegment = 0; xSegment < MAP_SEGMENTS; xSegment++) {
            float u0 = xSegment / (float)MAP_SEGMENTS;
            float u1 = (xSegment + 1) / (float)MAP_SEGMENTS;
            float x0 = lerp(-PROJECTION_HALF_SIZE, PROJECTION_HALF_SIZE, u0);
            float x1 = lerp(-PROJECTION_HALF_SIZE, PROJECTION_HALF_SIZE, u1);
            for (int ySegment = 0; ySegment < MAP_SEGMENTS; ySegment++) {
                float v0 = ySegment / (float)MAP_SEGMENTS;
                float v1 = (ySegment + 1) / (float)MAP_SEGMENTS;
                float y0 = lerp(PROJECTION_HALF_SIZE, -PROJECTION_HALF_SIZE, v0);
                float y1 = lerp(PROJECTION_HALF_SIZE, -PROJECTION_HALF_SIZE, v1);
                vertex(buffer, pose, x0, y1, u0, v1, lightCoords);
                vertex(buffer, pose, x1, y1, u1, v1, lightCoords);
                vertex(buffer, pose, x1, y0, u1, v0, lightCoords);
                vertex(buffer, pose, x0, y0, u0, v0, lightCoords);
            }
        }
    }

    private static void vertex(
            final VertexConsumer buffer,
            final PoseStack.Pose pose,
            final float x,
            final float y,
            final float u,
            final float v,
            final int lightCoords) {
        buffer.addVertex(pose, x, y, 0.0F)
                .setColor(MAP_COLOR)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, 0.0F, 0.0F, -1.0F);
    }

    private static float handOffset(final HumanoidArm arm) {
        return (arm == HumanoidArm.RIGHT ? 1.4F : -1.0F) * PROJECTION_HALF_SIZE * SIDE_OFFSET_SCALE;
    }

    private static int mapSpanBlocks(final int tileSizeBlocks) {
        int span = MIN_VANILLA_MAP_SPAN;
        while (span < tileSizeBlocks && span < MAX_VANILLA_MAP_SPAN) {
            span *= 2;
        }
        return span;
    }

    private static float lerp(final float min, final float max, final float delta) {
        return min + (max - min) * delta;
    }
}
