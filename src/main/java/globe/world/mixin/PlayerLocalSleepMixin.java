package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.GlobeLocalDaylight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class PlayerLocalSleepMixin {
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"
        )
    )
    private boolean keepSleepingWhileLocallyDark(BedRule rule, Level level, Operation<Boolean> original) {
        Player player = (Player) (Object) this;
        if (!GlobeLocalDaylight.enabled(level)) {
            return original.call(rule, level);
        }

        return GlobeLocalDaylight.canSleep(rule, level, BlockPos.containing(player.position()));
    }
}
