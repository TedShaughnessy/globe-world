package globe.world.client.render;

import globe.world.GlobeWorldBlocks;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class GlobeRenderers {
    private GlobeRenderers() {
    }

    public static void register() {
        BlockEntityRenderers.register(GlobeWorldBlocks.GLOBE_BLOCK_ENTITY, GlobeBlockEntityRenderer::new);
    }
}
