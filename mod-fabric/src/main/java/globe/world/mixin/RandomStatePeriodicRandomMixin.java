package globe.world.mixin;

import globe.world.util.PeriodicPositionalRandomFactory;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RandomState.class)
public class RandomStatePeriodicRandomMixin {
    @Inject(method = "aquiferRandom", at = @At("RETURN"), cancellable = true)
    private void wrapAquiferRandom(CallbackInfoReturnable<PositionalRandomFactory> cir) {
        cir.setReturnValue(PeriodicPositionalRandomFactory.chunk(cir.getReturnValue()));
    }

    @Inject(method = "oreRandom", at = @At("RETURN"), cancellable = true)
    private void wrapOreRandom(CallbackInfoReturnable<PositionalRandomFactory> cir) {
        cir.setReturnValue(PeriodicPositionalRandomFactory.block(cir.getReturnValue()));
    }
}
