package globe.world.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import globe.world.block.entity.GlobeBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
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
        if (!state.projectionBlockPos.equals(state.blockPos)) {
            state.projectionCaptured = false;
            state.projectionBlockPos = state.blockPos;
        }
        if (!state.projectionCaptured && GlobeMapTextureCache.textureForCurrentDimension() != null) {
            captureProjection(state, cameraPosition);
        }
    }

    @Override
    public void submit(
            final State state,
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final CameraRenderState camera) {
        Identifier texture = GlobeMapTextureCache.textureForCurrentDimension();
        if (texture == null) {
            GlobeSphereMesh.submit(
                    poseStack,
                    submitNodeCollector,
                    this.sprites.get(GlobeSphereMesh.BLANK_TEXTURE),
                    state.lightCoords,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            return;
        }

        if (!state.projectionCaptured) {
            captureProjection(state, camera.pos);
        }

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.projectionYawDegrees));
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        GlobeSphereMesh.submitProjected(
                poseStack,
                submitNodeCollector,
                texture,
                state.projectionCenterU,
                state.projectionCenterV,
                state.lightCoords,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(final GlobeBlockEntity blockEntity, final Vec3 cameraPosition) {
        return Vec3.atCenterOf(blockEntity.getBlockPos()).closerThan(cameraPosition, this.getViewDistance());
    }

    private static void captureProjection(final State state, final Vec3 cameraPosition) {
        state.projectionCenterU = GlobeMapTextureCache.projectionCenterU();
        state.projectionCenterV = GlobeMapTextureCache.projectionCenterV();
        Vec3 blockCenter = Vec3.atCenterOf(state.blockPos);
        double cameraX = cameraPosition.x - blockCenter.x;
        double cameraZ = cameraPosition.z - blockCenter.z;
        state.projectionYawDegrees = (float)Math.toDegrees(Math.atan2(-cameraX, -cameraZ));
        state.projectionCaptured = true;
        state.projectionBlockPos = state.blockPos;
    }

    public static class State extends BlockEntityRenderState {
        private boolean projectionCaptured;
        private BlockPos projectionBlockPos = BlockPos.ZERO;
        private float projectionCenterU = 0.5F;
        private float projectionCenterV = 0.5F;
        private float projectionYawDegrees;
    }
}
