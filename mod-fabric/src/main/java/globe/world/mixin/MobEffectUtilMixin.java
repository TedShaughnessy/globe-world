package globe.world.mixin;

import globe.world.util.CoordUtil;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.world.effect.MobEffectUtil.class)
public class MobEffectUtilMixin {
    @Inject(method = "addEffectToPlayersAround", at = @At("HEAD"), cancellable = true)
    private static void addEffectToWrappedPlayersAround(
            ServerLevel level,
            Entity source,
            Vec3 position,
            double radius,
            MobEffectInstance effectInstance,
            int displayEffectLimit,
            CallbackInfoReturnable<List<ServerPlayer>> cir) {
        Holder<MobEffect> effect = effectInstance.getEffect();
        double radiusSqr = radius * radius;
        List<ServerPlayer> players = level.getPlayers(input ->
                input.gameMode.isSurvival()
                        && (source == null || !source.isAlliedTo(input))
                        && CoordUtil.wrappedDistanceSqr(level, position.x(), position.y(), position.z(), input.getX(), input.getY(), input.getZ()) < radiusSqr
                        && (!input.hasEffect(effect)
                        || input.getEffect(effect).getAmplifier() < effectInstance.getAmplifier()
                        || input.getEffect(effect).endsWithin(displayEffectLimit - 1)));
        players.forEach(player -> player.addEffect(new MobEffectInstance(effectInstance), source));
        cir.setReturnValue(players);
    }
}
