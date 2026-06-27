package globe.world.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import globe.world.GlobeWorldBlocks;
import globe.world.block.entity.GlobeBlockEntity;
import globe.world.client.GlobeDebugState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.resources.Identifier;
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
        state.textureMapping = textureMapping(blockEntity.wrapAxis());
        state.projectorColor = GlobeWorldBlocks.projectorColor(blockEntity.getBlockState().getBlock());
    }

    @Override
    public void submit(
            final State state,
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final CameraRenderState camera) {
        Identifier texture = GlobeMapTextureCache.textureForCurrentDimension();
        if (texture == null) {
            GlobeToroidMesh.submit(
                    poseStack,
                    submitNodeCollector,
                    this.sprites.get(GlobeToroidMesh.BLANK_TEXTURE),
                    state.lightCoords,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            return;
        }

        if (GlobeDebugState.debugScreenEnabled()) {
            GlobeToroidMesh.submitComparison(
                    poseStack,
                    submitNodeCollector,
                    texture,
                    state.lightCoords,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            return;
        }

        GlobeToroidMesh.submitHologram(
                poseStack,
                submitNodeCollector,
                texture,
                state.textureMapping,
                state.projectorColor,
                state.lightCoords,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
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

    private static GlobeToroidMesh.TextureMapping textureMapping(final GlobeBlockEntity.WrapAxis axis) {
        return switch (axis) {
            case X_MAJOR_Z_MINOR -> GlobeToroidMesh.TextureMapping.X_MAJOR_Z_MINOR;
            case Z_MAJOR_X_MINOR -> GlobeToroidMesh.TextureMapping.Z_MAJOR_X_MINOR;
        };
    }

    public static class State extends BlockEntityRenderState {
        private GlobeToroidMesh.TextureMapping textureMapping = GlobeToroidMesh.TextureMapping.X_MAJOR_Z_MINOR;
        private int projectorColor = GlobeWorldBlocks.IRON_PROJECTOR_COLOR;
    }
}
