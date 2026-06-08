package globe.world.client.mixin;

import globe.world.util.CoordUtil;
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
        double aliasHandX = CoordUtil.virtualBlock(entity.level(), CoordUtil.wrapBlock(entity.level(), handX), state.x);
        double aliasHandZ = CoordUtil.virtualBlock(entity.level(), CoordUtil.wrapBlock(entity.level(), handZ), state.z);
        if (aliasHandX != handX || aliasHandZ != handZ) {
            state.lineOriginOffset = new Vec3(aliasHandX - state.x, state.lineOriginOffset.y, aliasHandZ - state.z);
        }
    }
}
