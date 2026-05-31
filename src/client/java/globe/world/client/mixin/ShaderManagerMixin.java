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
import java.util.Set;

@Mixin(ShaderManager.class)
public class ShaderManagerMixin {
    @Unique
    private static final Set<String> GLOBE_WORLD_CURVED_VERTEX_SHADERS = Set.of(
            "shaders/core/terrain.vsh",
            "shaders/core/entity.vsh",
            "shaders/core/block.vsh",
            "shaders/core/rendertype_entity_shadow.vsh",
            "shaders/core/rendertype_leash.vsh",
            "shaders/core/rendertype_lines.vsh",
            "shaders/core/rendertype_outline.vsh",
            "shaders/core/particle.vsh",
            "shaders/core/rendertype_clouds.vsh"
    );
    @Unique
    private static final ThreadLocal<Boolean> globeWorld$loadingCurvedVertexShader = ThreadLocal.withInitial(() -> false);

    @Inject(method = "loadShader", at = @At("HEAD"))
    private static void globeWorld$rememberShader(
            Identifier location,
            Resource resource,
            ShaderType type,
            Map<Identifier, Resource> files,
            ImmutableMap.Builder<?, String> output,
            CallbackInfo ci
    ) {
        globeWorld$loadingCurvedVertexShader.set(
                type == ShaderType.VERTEX
                        && "minecraft".equals(location.getNamespace())
                        && GLOBE_WORLD_CURVED_VERTEX_SHADERS.contains(location.getPath())
        );
    }

    @Redirect(
            method = "loadShader",
            at = @At(value = "INVOKE", target = "Lorg/apache/commons/io/IOUtils;toString(Ljava/io/Reader;)Ljava/lang/String;")
    )
    private static String globeWorld$readShaderSource(Reader reader) throws IOException {
        String source = IOUtils.toString(reader);
        if (!globeWorld$loadingCurvedVertexShader.get()) {
            return source;
        }
        return GlobeCurvatureShader.transformWorldVertexShader(source);
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
        globeWorld$loadingCurvedVertexShader.remove();
    }
}
