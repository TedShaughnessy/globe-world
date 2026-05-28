package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.ClientActionDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {
    @WrapOperation(
        method = "handleBlockBreakAction",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;mayInteract(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean rejectAliasBlockBreakOutsideCanonicalSimulation(
            ServerLevel level,
            Entity entity,
            BlockPos pos,
            Operation<Boolean> original) {
        boolean mayInteract = original.call(level, entity, pos);
        if (!mayInteract) {
            return false;
        }
        if (entity instanceof ServerPlayer player
                && ClientActionDiagnostics.shouldRejectAliasMutation(player, level, pos, "break_block")) {
            return false;
        }
        return true;
    }
}
