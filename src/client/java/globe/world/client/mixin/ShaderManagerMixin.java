package globe.world.client.mixin;

import com.mojang.blaze3d.shaders.ShaderType;
import com.google.common.collect.ImmutableMap;
import globe.world.client.GlobeCurvatureShader;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;

@Mixin(ShaderManager.class)
public class ShaderManagerMixin {
    @Unique
    private static final ThreadLocal<Boolean> globeWorld$loadingTerrainVertexShader = ThreadLocal.withInitial(() -> false);

    @Inject(method = "loadShader", at = @At("HEAD"))
    private static void globeWorld$rememberShader(
            Identifier location,
            Resource resource,
            ShaderType type,
            Map<Identifier, Resource> files,
            ImmutableMap.Builder<?, String> output,
            CallbackInfo ci
    ) {
        globeWorld$loadingTerrainVertexShader.set(
                type == ShaderType.VERTEX
                        && "minecraft".equals(location.getNamespace())
                        && "shaders/core/terrain.vsh".equals(location.getPath())
        );
    }

    @Redirect(
            method = "loadShader",
            at = @At(value = "INVOKE", target = "Lorg/apache/commons/io/IOUtils;toString(Ljava/io/Reader;)Ljava/lang/String;")
    )
    private static String globeWorld$readShaderSource(Reader reader) throws IOException {
        String source = IOUtils.toString(reader);
        if (!globeWorld$loadingTerrainVertexShader.get()) {
            return source;
        }
        return GlobeCurvatureShader.transformTerrainVertexShader(source);
    }

    @Inject(method = "loadShader", at = @At("RETURN"))
    private static void globeWorld$clearShader(
            Identifier location,
            Resource resource,
            ShaderType type,
            Map<Identifier, Resource> files,
            ImmutableMap.Builder<?, String> output,
            CallbackInfo ci
    ) {
        globeWorld$loadingTerrainVertexShader.remove();
    }
}
