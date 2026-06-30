package globe.world.mixin;

import globe.world.topology.TopologicalPoiQueries;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.Optional;

@Mixin(PortalForcer.class)
public class PortalForcerMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "findClosestPortalPosition", at = @At("HEAD"), cancellable = true)
    private void findClosestPortalAcrossSeams(
            BlockPos approximateExitPos,
            boolean toNether,
            WorldBorder worldBorder,
            CallbackInfoReturnable<Optional<BlockPos>> cir) {
        TopologyContext topology = TopologyContexts.forLevel(this.level);
        if (!topology.enabled()) {
            return;
        }

        BlockPos canonicalExit = topology.canonicalBlock(approximateExitPos);
        int radius = toNether ? 16 : 128;
        TopologicalPoiQueries.ensureLoadedAndValid(this.level, canonicalExit, radius);
        Vec3 exitPosition = Vec3.atLowerCornerOf(canonicalExit);
        cir.setReturnValue(TopologicalPoiQueries.recordsInSquare(
                        this.level,
                        type -> type.is(PoiTypes.NETHER_PORTAL),
                        canonicalExit,
                        radius,
                        PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos)
                .filter(worldBorder::isWithinBounds)
                .filter(pos -> this.level.getBlockState(pos).hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
                .min(Comparator
                        .comparingDouble((BlockPos pos) -> topology.wrappedDistanceSqr(
                                Vec3.atLowerCornerOf(pos),
                                exitPosition))
                        .thenComparingInt(Vec3i::getY)));
    }
}
