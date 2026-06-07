package globe.world.mixin;

import globe.world.util.EndPortalAvailability;
import globe.world.util.EndPortalFallback;
import globe.world.util.EnderEyeSignals;
import globe.world.util.ForcedProgressionStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderEyeItem.class)
public abstract class EnderEyeItemMixin extends Item {
    protected EnderEyeItemMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void useFallbackEndPortal(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!Level.OVERWORLD.equals(serverLevel.dimension())) {
            return;
        }
        if (isLookingAtEndPortalFrame(level, player)) {
            return;
        }
        EndPortalAvailability.Report report = EndPortalAvailability.classify(serverLevel);
        if (report.status() == EndPortalAvailability.Status.VANILLA_STRONGHOLD_PRESENT) {
            return;
        }
        if (report.status() == EndPortalAvailability.Status.FORCED_STRONGHOLD_AVAILABLE) {
            BlockPos forcedTarget = ForcedProgressionStructures.validatedForcedOverworldStrongholdTarget(serverLevel)
                    .orElse(null);
            if (forcedTarget != null) {
                cir.setReturnValue(EnderEyeSignals.launch(
                        serverLevel,
                        player,
                        hand,
                        (Item) (Object) this,
                        forcedTarget,
                        Vec3.atLowerCornerOf(forcedTarget)
                ));
                return;
            }
            cir.setReturnValue(EndPortalFallback.useEye(serverLevel, player, hand, (Item) (Object) this));
            return;
        }
        if (report.status() != EndPortalAvailability.Status.FALLBACK_PORTAL_REQUIRED
                && !EndPortalFallback.hasUsableSavedPortal(serverLevel)) {
            return;
        }

        cir.setReturnValue(EndPortalFallback.useEye(serverLevel, player, hand, (Item) (Object) this));
    }

    private static boolean isLookingAtEndPortalFrame(Level level, Player player) {
        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        return hitResult.getType() == HitResult.Type.BLOCK
                && level.getBlockState(hitResult.getBlockPos()).is(Blocks.END_PORTAL_FRAME);
    }
}
