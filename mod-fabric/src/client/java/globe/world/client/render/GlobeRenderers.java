package globe.world.client.render;

import globe.world.GlobeWorld;
import globe.world.GlobeWorldBlocks;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;

public final class GlobeRenderers {
    private GlobeRenderers() {
    }

    public static void register() {
        BlockEntityRenderers.register(GlobeWorldBlocks.GLOBE_BLOCK_ENTITY, GlobeBlockEntityRenderer::new);
        SpecialModelRenderers.ID_MAPPER.put(
                Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "globe"),
                GlobeSpecialRenderer.Unbaked.MAP_CODEC);
    }
}
