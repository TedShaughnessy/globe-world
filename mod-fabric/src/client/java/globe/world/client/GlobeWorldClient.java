package globe.world.client;

import globe.world.client.render.GlobeRenderers;
import net.fabricmc.api.ClientModInitializer;

public class GlobeWorldClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		GlobeRenderers.register();
		GlobeClientNetworking.register();
		GlobeCurvatureShader.initialize();
		GlobeClientDebugCommands.register();
	}
}
