package globe.world.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class GlobeSpecialRenderer implements NoDataSpecialModelRenderer {
    private static final float HELD_PROJECTION_Y_ROTATION_DEGREES = 90.0F;
    private final SpriteGetter sprites;

    public GlobeSpecialRenderer(final SpriteGetter sprites) {
        this.sprites = sprites;
    }

    @Override
    public void submit(
            final PoseStack poseStack,
            final SubmitNodeCollector submitNodeCollector,
            final int lightCoords,
            final int overlayCoords,
            final boolean hasFoil,
            final int outlineColor) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(HELD_PROJECTION_Y_ROTATION_DEGREES));
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        Identifier texture = GlobeMapTextureCache.textureForCurrentDimension();
        if (texture == null) {
            TextureAtlasSprite sprite = this.sprites.get(GlobeSphereMesh.BLANK_TEXTURE);
            GlobeSphereMesh.submit(poseStack, submitNodeCollector, sprite, lightCoords, overlayCoords);
            poseStack.popPose();
            return;
        }

        GlobeSphereMesh.submitProjected(
                poseStack,
                submitNodeCollector,
                texture,
                GlobeMapTextureCache.projectionCenterU(),
                GlobeMapTextureCache.projectionCenterV(),
                lightCoords,
                overlayCoords);
        poseStack.popPose();
    }

    @Override
    public void getExtents(final Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0.07F, 0.07F, 0.07F));
        output.accept(new Vector3f(0.93F, 0.93F, 0.93F));
    }

    public record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<GlobeSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(new GlobeSpecialRenderer.Unbaked());

        @Override
        public MapCodec<GlobeSpecialRenderer.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public GlobeSpecialRenderer bake(final SpecialModelRenderer.BakingContext context) {
            return new GlobeSpecialRenderer(context.sprites());
        }
    }
}
