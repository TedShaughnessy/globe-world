package globe.world.mixin;

import globe.world.topology.TopologyContexts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BulkSectionAccess.class)
public class BulkSectionAccessMixin {
    @Shadow
    @Final
    private LevelAccessor level;

    @ModifyVariable(method = "getSection", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos canonicalizeBulkSectionPos(BlockPos pos) {
        if (this.level instanceof ServerLevelAccessor serverLevelAccessor) {
            return TopologyContexts.forLevel(serverLevelAccessor.getLevel()).canonicalBlock(pos);
        }
        if (this.level instanceof Level concreteLevel) {
            return TopologyContexts.forLevel(concreteLevel).canonicalBlock(pos);
        }
        return TopologyContexts.currentOrOverworld().canonicalBlock(pos);
    }
}
