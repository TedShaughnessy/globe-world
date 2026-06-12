package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightningBolt.class)
public class LightningBoltMixin {
    @WrapOperation(
        method = "lambda$tick$1",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;distanceTo(Lnet/minecraft/world/entity/Entity;)F"
        )
    )
    private float useWrappedDistanceToLightning(ServerPlayer player, Entity lightning, Operation<Float> original) {
        double distanceSqr = CoordUtil.wrappedDistanceSqr(player.level(), player.getX(), player.getY(), player.getZ(), lightning.getX(), lightning.getY(), lightning.getZ());
        return (float) Math.sqrt(distanceSqr);
    }
}
