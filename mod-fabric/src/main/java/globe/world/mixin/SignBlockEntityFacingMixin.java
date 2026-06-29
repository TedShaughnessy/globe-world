package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SignBlockEntity.class)
public class SignBlockEntityFacingMixin {
    @WrapOperation(
        method = "isFacingFrontText",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/SignBlockEntity;getBlockPos()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos virtualizeSignFacingPos(
            SignBlockEntity sign,
            Operation<BlockPos> original,
            Player player) {
        BlockPos pos = original.call(sign);
        TopologyContext topology = TopologyContexts.forLevel(player.level());
        return topology.virtualBlockForViewer(topology.canonicalBlock(pos), player.position());
    }
}
