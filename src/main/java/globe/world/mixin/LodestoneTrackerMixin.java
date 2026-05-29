package globe.world.mixin;

import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.component.LodestoneTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LodestoneTracker.class)
public class LodestoneTrackerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void validateCanonicalLodestone(ServerLevel level, CallbackInfoReturnable<LodestoneTracker> cir) {
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (!tiling.enabled()) {
            return;
        }

        LodestoneTracker tracker = (LodestoneTracker) (Object) this;
        Optional<GlobalPos> target = tracker.target();
        if (!tracker.tracked() || target.isEmpty() || target.get().dimension() != level.dimension()) {
            return;
        }

        BlockPos canonicalPos = CoordUtil.wrapBlockPos(tiling, target.get().pos());
        boolean valid = level.isInWorldBounds(canonicalPos)
                && level.getPoiManager().existsAtPosition(PoiTypes.LODESTONE, canonicalPos);
        cir.setReturnValue(valid ? tracker : new LodestoneTracker(Optional.empty(), true));
    }
}
