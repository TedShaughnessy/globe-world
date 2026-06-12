package globe.world.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import globe.world.client.GlobeSkyHorizon;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Matrix4fStack;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
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

    @WrapOperation(
            method = "renderSkyDisc",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
            )
    )
    private GpuBufferSlice globeWorld$offsetSkyDiscTransform(
            DynamicUniforms dynamicUniforms,
            Matrix4fc modelView,
            Vector4fc colorModulator,
            Vector3fc modelOffset,
            Matrix4fc textureMatrix,
            Operation<GpuBufferSlice> original
    ) {
        return original.call(
                dynamicUniforms,
                globeWorld$offsetModelViewCopy(modelView, GlobeSkyHorizon.skyDiscYOffset()),
                colorModulator,
                modelOffset,
                textureMatrix
        );
    }

    @WrapOperation(
            method = "renderDarkDisc",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix4fStack;translate(FFF)Lorg/joml/Matrix4f;"
            )
    )
    private Matrix4f globeWorld$offsetDarkDiscTransform(
            Matrix4fStack modelViewStack,
            float x,
            float y,
            float z,
            Operation<Matrix4f> original
    ) {
        globeWorld$translateHorizonOffset(modelViewStack, GlobeSkyHorizon.skyDiscYOffset());
        return original.call(modelViewStack, x, y, z);
    }

    @WrapOperation(
            method = {"renderSun", "renderMoon", "renderSunriseAndSunset"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix4fStack;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;"
            )
    )
    private Matrix4f globeWorld$offsetHorizonEffectTransform(
            Matrix4fStack modelViewStack,
            Matrix4fc pose,
            Operation<Matrix4f> original
    ) {
        globeWorld$translateHorizonOffset(modelViewStack, GlobeSkyHorizon.horizonEffectYOffset());
        return original.call(modelViewStack, pose);
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

    @Unique
    private static Matrix4f globeWorld$offsetModelViewCopy(Matrix4fc modelView, float yOffset) {
        Matrix4f offsetModelView = new Matrix4f(modelView);
        globeWorld$translateHorizonOffset(offsetModelView, yOffset);
        return offsetModelView;
    }

    @Unique
    private static void globeWorld$translateHorizonOffset(Matrix4f modelView, float yOffset) {
        if (yOffset != 0.0F) {
            modelView.translate(0.0F, yOffset, 0.0F);
        }
    }
}
