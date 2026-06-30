package globe.world.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import globe.world.GlobeWorldBlocks;
import globe.world.atlas.GlobeAtlasSurvey;
import globe.world.network.GlobeAtlasSurveyWindowPayload;
import globe.world.topology.TileGeometry;
import globe.world.util.DimensionTiling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GlobeHeldMapRenderer {
    private static final int MIN_VANILLA_MAP_SPAN = 128;
    private static final int MAX_VANILLA_MAP_SPAN = 2048;
    private static final int MAP_SEGMENTS = 24;
    private static final float PROJECTION_SIZE = 0.40F;
    private static final float PROJECTION_HALF_SIZE = PROJECTION_SIZE * 0.5F;
    private static final float PROJECTION_CAMERA_BOW = 0.03F;
    private static final float SCREEN_HAND_X = 0.46F;
    private static final float SCREEN_HAND_Y = 0.08F;
    private static final float SCREEN_HAND_Z = -0.48F;
    private static final float SCREEN_HAND_YAW = 16.0F;
    private static final float SCREEN_HAND_ROLL = 2.0F;
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
        if (!tiling.enabled()
                || !Level.OVERWORLD.equals(client.level.dimension())) {
            return;
        }

        TileGeometry geometry = TileGeometry.create(tiling);
        Vec3 canonical = geometry.canonicalBlock(client.player.position());
        double centerX = canonical.x();
        double centerZ = canonical.z();
        Identifier texture = GlobeAtlasSurvey.surveyMode(tiling)
                ? surveyTexture(tiling, centerX, centerZ)
                : literalTexture(tiling, centerX, centerZ);
        if (texture == null) {
            return;
        }

        poseStack.pushPose();
        int armSign = armSign(arm);
        poseStack.translate(armSign * SCREEN_HAND_X, SCREEN_HAND_Y, SCREEN_HAND_Z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-armSign * SCREEN_HAND_YAW));
        poseStack.mulPose(Axis.ZP.rotationDegrees(armSign * SCREEN_HAND_ROLL));
        poseStack.mulPose(Axis.ZP.rotationDegrees(yawDegrees));

        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.textSeeThrough(texture),
                (pose, buffer) -> renderProjection(buffer, pose, FULL_BRIGHT_LIGHT));
        poseStack.popPose();
    }

    private static Identifier literalTexture(final DimensionTiling tiling, final double centerX, final double centerZ) {
        int mapSpanBlocks = mapSpanBlocks(tiling.tileSizeBlocks());
        return GlobeMapTextureCache.updateHeldViewportForCurrentDimension(
                centerX,
                centerZ,
                mapSpanBlocks,
                tiling);
    }

    private static Identifier surveyTexture(final DimensionTiling tiling, final double centerX, final double centerZ) {
        ChunkPos center = TileGeometry.create(tiling).canonicalChunk(
                SectionPos.blockToSectionCoord(Mth.floor(centerX)),
                SectionPos.blockToSectionCoord(Mth.floor(centerZ)));
        return GlobeAtlasSurveyTextureCache.heldTextureForCurrentDimension(
                center.x(),
                center.z(),
                GlobeAtlasSurveyWindowPayload.HELD_WINDOW_CHUNKS);
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
        buffer.addVertex(pose, x, y, cameraBow(x, y))
                .setColor(MAP_COLOR)
                .setUv(u, v)
                .setLight(lightCoords);
    }

    private static float cameraBow(final float x, final float y) {
        float normalizedX = x / PROJECTION_HALF_SIZE;
        float normalizedY = y / PROJECTION_HALF_SIZE;
        float distanceSqr = Math.min(1.0F, normalizedX * normalizedX + normalizedY * normalizedY);
        return PROJECTION_CAMERA_BOW * (1.0F - distanceSqr);
    }

    private static int armSign(final HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT ? 1 : -1;
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
