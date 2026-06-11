package globe.world.client.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class GlobeSodiumMixinPlugin implements IMixinConfigPlugin {
    private static final String SUPPORTED_SODIUM_VERSION = "0.8.12+mc26.1.2";
    private static final Logger LOGGER = LoggerFactory.getLogger("globe-world");
    private static final boolean SODIUM_LOADED = FabricLoader.getInstance().isModLoaded("sodium");
    private static final String SODIUM_VERSION = FabricLoader.getInstance()
            .getModContainer("sodium")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("");
    private static final boolean SUPPORTED_SODIUM_LOADED = SODIUM_LOADED
            && SUPPORTED_SODIUM_VERSION.equals(SODIUM_VERSION);

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info(
                "Sodium compatibility mixins {}.",
                sodiumStatus()
        );
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return SUPPORTED_SODIUM_LOADED;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static String sodiumStatus() {
        if (!SODIUM_LOADED) {
            return "disabled because Sodium is not loaded";
        }
        if (!SUPPORTED_SODIUM_LOADED) {
            return "disabled for unsupported Sodium version " + SODIUM_VERSION;
        }
        return "enabled for Sodium " + SODIUM_VERSION;
    }
}
