package globe.world.util;

import globe.world.topology.TopologyContexts;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public final class EnderEyeSignals {
    private EnderEyeSignals() {
    }

    public static InteractionResult launch(
            ServerLevel level,
            Player player,
            InteractionHand hand,
            Item eyeItem,
            BlockPos advancementTarget,
            Vec3 signalTarget) {
        player.startUsingItem(hand);

        ItemStack stack = player.getItemInHand(hand);
        Vec3 canonicalPlayer = TopologyContexts.forLevel(level).canonicalBlock(player.position());
        EyeOfEnder eye = new EyeOfEnder(
                level,
                canonicalPlayer.x(),
                canonicalPlayer.y() + player.getBbHeight() * 0.5D,
                canonicalPlayer.z()
        );
        eye.setItem(stack);
        eye.signalTo(signalTarget);
        level.gameEvent(GameEvent.PROJECTILE_SHOOT, eye.position(), GameEvent.Context.of(player));
        level.addFreshEntity(eye);

        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.USED_ENDER_EYE.trigger(serverPlayer, advancementTarget);
        }

        float pitch = Mth.lerp(level.getRandom().nextFloat(), 0.33F, 0.5F);
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.ENDER_EYE_LAUNCH,
                SoundSource.NEUTRAL,
                1.0F,
                pitch
        );
        stack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(eyeItem));
        return InteractionResult.SUCCESS_SERVER;
    }
}
