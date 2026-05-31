package globe.world.client.mixin;

import globe.world.client.GlobeCurvedRaycast;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
    @Redirect(
            method = "pick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;")
    )
    private static HitResult globeWorld$pickCurvedBlocks(Entity cameraEntity, double range, float partialTicks, boolean withLiquids) {
        return GlobeCurvedRaycast.pickBlock(cameraEntity, range, partialTicks, withLiquids);
    }
}
