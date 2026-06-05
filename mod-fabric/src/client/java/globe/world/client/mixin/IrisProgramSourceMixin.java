package globe.world.client.mixin;

import globe.world.client.GlobeCurvatureShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.irisshaders.iris.shaderpack.programs.ProgramSource", remap = false)
public class IrisProgramSourceMixin {
    @ModifyVariable(
            method = "<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;Lnet/irisshaders/iris/shaderpack/properties/ShaderProperties;Lnet/irisshaders/iris/gl/blending/BlendModeOverride;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1,
            require = 0
    )
    private static String globeWorld$transformVertexSource(String source) {
        return GlobeCurvatureShader.transformIrisShaderPackSource(source);
    }

    @ModifyVariable(
            method = "<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;Lnet/irisshaders/iris/shaderpack/properties/ShaderProperties;Lnet/irisshaders/iris/gl/blending/BlendModeOverride;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2,
            require = 0
    )
    private static String globeWorld$transformGeometrySource(String source) {
        return GlobeCurvatureShader.transformIrisShaderPackSource(source);
    }

    @ModifyVariable(
            method = "<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;Lnet/irisshaders/iris/shaderpack/properties/ShaderProperties;Lnet/irisshaders/iris/gl/blending/BlendModeOverride;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 3,
            require = 0
    )
    private static String globeWorld$transformTessControlSource(String source) {
        return GlobeCurvatureShader.transformIrisShaderPackSource(source);
    }

    @ModifyVariable(
            method = "<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;Lnet/irisshaders/iris/shaderpack/properties/ShaderProperties;Lnet/irisshaders/iris/gl/blending/BlendModeOverride;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 4,
            require = 0
    )
    private static String globeWorld$transformTessEvalSource(String source) {
        return GlobeCurvatureShader.transformIrisShaderPackSource(source);
    }

    @ModifyVariable(
            method = "<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;Lnet/irisshaders/iris/shaderpack/properties/ShaderProperties;Lnet/irisshaders/iris/gl/blending/BlendModeOverride;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 5,
            require = 0
    )
    private static String globeWorld$transformFragmentSource(String source) {
        return GlobeCurvatureShader.transformIrisShaderPackSource(source);
    }
}
