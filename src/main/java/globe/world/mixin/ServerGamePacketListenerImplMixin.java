package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.ClientActionDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
    @WrapOperation(
        method = "handleUseItemOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;mayInteract(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean rejectAliasUseItemOnOutsideCanonicalSimulation(
            ServerLevel level,
            Entity entity,
            BlockPos pos,
            Operation<Boolean> original) {
        boolean mayInteract = original.call(level, entity, pos);
        if (!mayInteract) {
            return false;
        }
        if (entity instanceof ServerPlayer player
                && ClientActionDiagnostics.shouldRejectAliasMutation(player, level, pos, "use_item_on")) {
            return false;
        }
        return true;
    }
}
