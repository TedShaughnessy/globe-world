package globe.world.client.mixin;

import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHookRenderer.class)
public class FishingHookRendererMixin {
    @Inject(
            method = "extractRenderState("
                    + "Lnet/minecraft/world/entity/projectile/FishingHook;"
                    + "Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;"
                    + "F)V",
            at = @At("TAIL")
    )
    private void globeWorld$alignLineWithHookAlias(
            FishingHook entity,
            FishingHookRenderState state,
            float partialTicks,
            CallbackInfo ci
    ) {
        Player owner = entity.getPlayerOwner();
        if (owner == null) {
            return;
        }

        double handX = state.x + state.lineOriginOffset.x;
        double handZ = state.z + state.lineOriginOffset.z;
        TopologyContext topology = TopologyContexts.forLevel(entity.level());
        Vec3 canonicalHand = topology.canonicalBlock(new Vec3(handX, state.y + state.lineOriginOffset.y, handZ));
        Vec3 aliasHand = topology.virtualBlockForViewer(canonicalHand, new Vec3(state.x, state.y, state.z));
        if (aliasHand.x() != handX || aliasHand.z() != handZ) {
            state.lineOriginOffset = new Vec3(
                    aliasHand.x() - state.x,
                    state.lineOriginOffset.y,
                    aliasHand.z() - state.z);
        }
    }
}
