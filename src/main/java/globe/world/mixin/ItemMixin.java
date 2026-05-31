package globe.world.mixin;

import globe.world.util.GlobeCurvedRaycast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class ItemMixin {
    @Inject(
            method = "getPlayerPOVHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/ClipContext$Fluid;)Lnet/minecraft/world/phys/BlockHitResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void globeWorld$pickCurvedItemTarget(Level level, Player player, ClipContext.Fluid fluid, CallbackInfoReturnable<BlockHitResult> cir) {
        cir.setReturnValue(GlobeCurvedRaycast.pickBlock(level, player, player.blockInteractionRange(), 1.0F, fluid));
    }
}
