package globe.world.client;

import globe.world.GlobeWorld;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class GlobeIrisShaderBridge {
    private static final String IRIS_MOD_ID = "iris";

    private GlobeIrisShaderBridge() {
    }

    static void reloadShadersIfPresent() {
        if (!FabricLoader.getInstance().isModLoaded(IRIS_MOD_ID)) {
            return;
        }

        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Method reload = irisClass.getMethod("reload");
            reload.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            GlobeWorld.LOGGER.warn("Iris is loaded, but Globe World could not find Iris.reload() for curvature shader refresh.", e);
        } catch (IllegalAccessException | InvocationTargetException e) {
            GlobeWorld.LOGGER.warn("Failed to refresh Iris shaders after Globe World curvature changed.", e);
        }
    }
}
