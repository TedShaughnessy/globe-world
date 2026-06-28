package globe.world.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import globe.world.GlobeWorldBlocks;
import globe.world.atlas.GlobeAtlasSurvey;
import globe.world.block.GlobeBlock;
import globe.world.block.entity.GlobeBlockEntity;
import globe.world.client.GlobeDebugState;
import globe.world.network.GlobeAtlasSurveyWindowPayload;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class GlobeBlockEntityRenderer implements BlockEntityRenderer<GlobeBlockEntity, GlobeBlockEntityRenderer.State> {
    private static final float SURVEY_LOW = -0.35F;
    private static final float SURVEY_HIGH = 1.35F;
    private static final float SURVEY_Y = 1.06F;
    private static final float SURVEY_DOME_HEIGHT = 0.10F;
    private static final int SURVEY_SEGMENTS = 24;
    private static final int SURVEY_LIGHT = 0x00F000F0;
    private static final int SURVEY_COLOR = 0xDCFFFFFF;

    private final SpriteGetter sprites;

    public GlobeBlockEntityRenderer(final BlockEntityRendererProvider.Context context) {
        this.sprites = context.sprites();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            final GlobeBlockEntity blockEntity,
            final State state,
            final float partialTicks,
            final Vec3 cameraPosition,
            final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.projectionEnabled = blockEntity.projectionEnabled();
        state.projectorColor = GlobeWorldBlocks.projectorColor(blockEntity.getBlockState().getBlock());
        state.face = blockEntity.getBlockState().getValue(GlobeBlock.FACE);
        state.facing = blockEntity.getBlockState().getValue(GlobeBlock.FACING);
        if (blockEntity.getLevel() != null) {
            DimensionTiling tiling = DimensionTiling.forLevel(blockEntity.getLevel());
            state.centerChunkX = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(blockEntity.getBlockPos().getX()));
            state.centerChunkZ = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(blockEntity.getBlockPos().getZ()));
        }
    }

    @Override
    public void submit(
            final State state,
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final CameraRenderState camera) {
        poseStack.pushPose();
        applyPlacementTransform(poseStack, state.face, state.facing);
        Minecraft client = Minecraft.getInstance();
        if (!state.projectionEnabled
                || client.level == null) {
            poseStack.popPose();
            return;
        }

        if (GlobeAtlasSurvey.surveyMode(DimensionTiling.forLevel(client.level))) {
            Identifier texture = GlobeAtlasSurveyTextureCache.textureForCurrentDimension(
                    state.centerChunkX,
                    state.centerChunkZ,
                    GlobeAtlasSurveyWindowPayload.PLACED_WINDOW_CHUNKS);
            if (texture != null) {
                submitSurveyPlane(poseStack, submitNodeCollector, texture);
            }
            poseStack.popPose();
            return;
        }

        Identifier texture = GlobeMapTextureCache.textureForCurrentDimension();
        if (texture == null) {
            GlobeToroidMesh.submit(
                    poseStack,
                    submitNodeCollector,
                    this.sprites.get(GlobeToroidMesh.BLANK_TEXTURE),
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
            return;
        }

        if (GlobeDebugState.debugScreenEnabled()) {
            GlobeToroidMesh.submitComparison(
                    poseStack,
                    submitNodeCollector,
                    texture,
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
            return;
        }

        GlobeToroidMesh.submitHologram(
                poseStack,
                submitNodeCollector,
                texture,
                GlobeToroidMesh.TextureMapping.X_MAJOR_Z_MINOR,
                state.projectorColor,
                state.lightCoords,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public boolean shouldRender(final GlobeBlockEntity blockEntity, final Vec3 cameraPosition) {
        return Vec3.atCenterOf(blockEntity.getBlockPos()).closerThan(cameraPosition, this.getViewDistance());
    }

    private static void applyPlacementTransform(final PoseStack poseStack, final AttachFace face, final Direction facing) {
        poseStack.translate(0.5F, 0.5F, 0.5F);
        switch (face) {
            case CEILING -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(facingYawDegrees(facing) + 180.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            }
            case FLOOR -> poseStack.mulPose(Axis.YP.rotationDegrees(facingYawDegrees(facing)));
            case WALL -> {
                switch (facing) {
                    case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                    case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                    case EAST -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
                    case WEST -> poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
                    default -> {
                    }
                }
            }
        }
        poseStack.translate(-0.5F, -0.5F, -0.5F);
    }

    private static float facingYawDegrees(final Direction facing) {
        return facing.toYRot() - 180.0F;
    }

    private static void submitSurveyPlane(
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final Identifier texture) {
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.textSeeThrough(texture),
                (pose, buffer) -> renderSurveyPlane(buffer, pose));
    }

    private static void renderSurveyPlane(final VertexConsumer buffer, final PoseStack.Pose pose) {
        for (int xSegment = 0; xSegment < SURVEY_SEGMENTS; xSegment++) {
            float u0 = xSegment / (float)SURVEY_SEGMENTS;
            float u1 = (xSegment + 1) / (float)SURVEY_SEGMENTS;
            float x0 = lerp(SURVEY_LOW, SURVEY_HIGH, u0);
            float x1 = lerp(SURVEY_LOW, SURVEY_HIGH, u1);
            for (int zSegment = 0; zSegment < SURVEY_SEGMENTS; zSegment++) {
                float v0 = zSegment / (float)SURVEY_SEGMENTS;
                float v1 = (zSegment + 1) / (float)SURVEY_SEGMENTS;
                float z0 = lerp(SURVEY_HIGH, SURVEY_LOW, v0);
                float z1 = lerp(SURVEY_HIGH, SURVEY_LOW, v1);
                vertex(buffer, pose, x0, z0, u0, v0);
                vertex(buffer, pose, x1, z0, u1, v0);
                vertex(buffer, pose, x1, z1, u1, v1);
                vertex(buffer, pose, x0, z1, u0, v1);
            }
        }
    }

    private static void vertex(
            final VertexConsumer buffer,
            final PoseStack.Pose pose,
            final float x,
            final float z,
            final float u,
            final float v) {
        float y = surveyDomeY(x, z);
        float normalizedX = normalizedSurveyCoordinate(x);
        float normalizedZ = normalizedSurveyCoordinate(z);
        float normalScale = SURVEY_DOME_HEIGHT / ((SURVEY_HIGH - SURVEY_LOW) * 0.5F);
        float normalX = normalizedX * normalScale;
        float normalZ = normalizedZ * normalScale;
        float normalY = 1.0F;
        float length = (float)Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        buffer.addVertex(pose, x, y, z)
                .setColor(SURVEY_COLOR)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(SURVEY_LIGHT)
                .setNormal(pose, normalX / length, normalY / length, normalZ / length);
    }

    private static float surveyDomeY(final float x, final float z) {
        float normalizedX = normalizedSurveyCoordinate(x);
        float normalizedZ = normalizedSurveyCoordinate(z);
        float radiusSqr = Math.min(1.0F, (normalizedX * normalizedX + normalizedZ * normalizedZ) * 0.5F);
        return SURVEY_Y + SURVEY_DOME_HEIGHT * (1.0F - radiusSqr);
    }

    private static float normalizedSurveyCoordinate(final float coordinate) {
        return (coordinate - 0.5F) / ((SURVEY_HIGH - SURVEY_LOW) * 0.5F);
    }

    private static float lerp(final float min, final float max, final float delta) {
        return min + (max - min) * delta;
    }

    public static class State extends BlockEntityRenderState {
        private boolean projectionEnabled;
        private int projectorColor = GlobeWorldBlocks.IRON_PROJECTOR_COLOR;
        private AttachFace face = AttachFace.FLOOR;
        private Direction facing = Direction.NORTH;
        private int centerChunkX;
        private int centerChunkZ;
    }
}
