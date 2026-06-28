package globe.world.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import globe.world.GlobeWorldBlocks;
import globe.world.atlas.GlobeAtlasSurvey;
import globe.world.block.GlobeBlock;
import globe.world.block.entity.GlobeBlockEntity;
import globe.world.client.GlobeDebugState;
import globe.world.util.DimensionTiling;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class GlobeBlockEntityRenderer implements BlockEntityRenderer<GlobeBlockEntity, GlobeBlockEntityRenderer.State> {
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
                || client.level == null
                || GlobeAtlasSurvey.surveyMode(DimensionTiling.forLevel(client.level))) {
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
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
            return;
        }

        if (GlobeDebugState.debugScreenEnabled()) {
            GlobeToroidMesh.submitComparison(
                    poseStack,
                    submitNodeCollector,
                    texture,
                    state.lightCoords,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
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
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
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

    public static class State extends BlockEntityRenderState {
        private boolean projectionEnabled;
        private int projectorColor = GlobeWorldBlocks.IRON_PROJECTOR_COLOR;
        private AttachFace face = AttachFace.FLOOR;
        private Direction facing = Direction.NORTH;
    }
}
