package globe.world.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import globe.world.client.GlobeSkyHorizon;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void globeWorld$updateCurvedHorizon(
            ClientLevel level,
            float partialTicks,
            Camera camera,
            SkyRenderState state,
            CallbackInfo ci
    ) {
        GlobeSkyHorizon.update(level, camera);
    }

    @Inject(method = "renderSkyDisc", at = @At("HEAD"))
    private void globeWorld$pushSkyDiscHorizonOffset(int skyColor, CallbackInfo ci) {
        globeWorld$pushHorizonOffset(GlobeSkyHorizon.skyDiscYOffset());
    }

    @Inject(method = "renderSkyDisc", at = @At("RETURN"))
    private void globeWorld$popSkyDiscHorizonOffset(int skyColor, CallbackInfo ci) {
        RenderSystem.getModelViewStack().popMatrix();
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"))
    private void globeWorld$pushDarkDiscHorizonOffset(CallbackInfo ci) {
        globeWorld$pushHorizonOffset(GlobeSkyHorizon.skyDiscYOffset());
    }

    @Inject(method = "renderDarkDisc", at = @At("RETURN"))
    private void globeWorld$popDarkDiscHorizonOffset(CallbackInfo ci) {
        RenderSystem.getModelViewStack().popMatrix();
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
    private void globeWorld$pushCelestialHorizonOffset(
            PoseStack poseStack,
            float sunAngle,
            float moonAngle,
            float starAngle,
            MoonPhase moonPhase,
            float rainBrightness,
            float starBrightness,
            CallbackInfo ci
    ) {
        globeWorld$pushHorizonOffset(GlobeSkyHorizon.celestialYOffset());
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("RETURN"))
    private void globeWorld$popCelestialHorizonOffset(
            PoseStack poseStack,
            float sunAngle,
            float moonAngle,
            float starAngle,
            MoonPhase moonPhase,
            float rainBrightness,
            float starBrightness,
            CallbackInfo ci
    ) {
        RenderSystem.getModelViewStack().popMatrix();
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"))
    private void globeWorld$pushSunriseHorizonOffset(PoseStack poseStack, float sunAngle, int sunriseAndSunsetColor, CallbackInfo ci) {
        globeWorld$pushHorizonOffset(GlobeSkyHorizon.celestialYOffset());
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("RETURN"))
    private void globeWorld$popSunriseHorizonOffset(PoseStack poseStack, float sunAngle, int sunriseAndSunsetColor, CallbackInfo ci) {
        RenderSystem.getModelViewStack().popMatrix();
    }

    @Unique
    private static void globeWorld$pushHorizonOffset(float yOffset) {
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        if (yOffset != 0.0F) {
            modelViewStack.translate(0.0F, yOffset, 0.0F);
        }
    }
}
