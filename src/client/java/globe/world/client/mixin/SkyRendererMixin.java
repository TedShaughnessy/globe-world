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
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
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

    @Inject(method = "renderSun", at = @At("HEAD"))
    private void globeWorld$pushSunHorizonOffset(
            float rainBrightness,
            PoseStack poseStack,
            CallbackInfo ci
    ) {
        globeWorld$pushHorizonOffset(GlobeSkyHorizon.horizonEffectYOffset());
    }

    @Inject(method = "renderSun", at = @At("RETURN"))
    private void globeWorld$popSunHorizonOffset(
            float rainBrightness,
            PoseStack poseStack,
            CallbackInfo ci
    ) {
        RenderSystem.getModelViewStack().popMatrix();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"))
    private void globeWorld$pushMoonHorizonOffset(
            MoonPhase moonPhase,
            float rainBrightness,
            PoseStack poseStack,
            CallbackInfo ci
    ) {
        globeWorld$pushHorizonOffset(GlobeSkyHorizon.horizonEffectYOffset());
    }

    @Inject(method = "renderMoon", at = @At("RETURN"))
    private void globeWorld$popMoonHorizonOffset(
            MoonPhase moonPhase,
            float rainBrightness,
            PoseStack poseStack,
            CallbackInfo ci
    ) {
        RenderSystem.getModelViewStack().popMatrix();
    }

    @ModifyConstant(method = {"renderSun", "renderMoon"}, constant = @Constant(floatValue = 100.0F))
    private float globeWorld$increaseCelestialRadius(float radius) {
        return GlobeSkyHorizon.celestialRadius();
    }

    @ModifyConstant(method = "renderSun", constant = @Constant(floatValue = 30.0F))
    private float globeWorld$keepSunAngularSize(float size) {
        return size * GlobeSkyHorizon.celestialScale();
    }

    @ModifyConstant(method = "renderMoon", constant = @Constant(floatValue = 20.0F))
    private float globeWorld$keepMoonAngularSize(float size) {
        return size * GlobeSkyHorizon.celestialScale();
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"))
    private void globeWorld$pushSunriseHorizonOffset(PoseStack poseStack, float sunAngle, int sunriseAndSunsetColor, CallbackInfo ci) {
        globeWorld$pushHorizonOffset(GlobeSkyHorizon.horizonEffectYOffset());
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
