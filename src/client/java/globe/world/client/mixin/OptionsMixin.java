package globe.world.client.mixin;

import globe.world.client.GlobeCurvatureShader;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public class OptionsMixin {
    @Inject(method = "getEffectiveRenderDistance", at = @At("RETURN"), cancellable = true)
    private void globeWorld$capCurvedRenderDistance(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(GlobeCurvatureShader.effectiveRenderDistanceChunks(cir.getReturnValue()));
    }
}
