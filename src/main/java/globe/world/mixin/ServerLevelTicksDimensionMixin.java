package globe.world.mixin;

import globe.world.util.DimensionTilingAware;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public class ServerLevelTicksDimensionMixin {
    @Inject(method = "getBlockTicks", at = @At("RETURN"))
    private void attachBlockTickDimension(CallbackInfoReturnable<LevelTicks<Block>> cir) {
        ((DimensionTilingAware) cir.getReturnValue()).globeWorld$setDimension(((ServerLevel) (Object) this).dimension());
    }

    @Inject(method = "getFluidTicks", at = @At("RETURN"))
    private void attachFluidTickDimension(CallbackInfoReturnable<LevelTicks<Fluid>> cir) {
        ((DimensionTilingAware) cir.getReturnValue()).globeWorld$setDimension(((ServerLevel) (Object) this).dimension());
    }
}
