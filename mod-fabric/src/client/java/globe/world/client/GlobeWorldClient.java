package globe.world.client;

import net.fabricmc.api.ClientModInitializer;

public class GlobeWorldClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		GlobeClientNetworking.register();
		GlobeCurvatureShader.initialize();
		GlobeClientDebugCommands.register();
	}
}
