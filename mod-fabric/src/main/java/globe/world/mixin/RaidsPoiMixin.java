package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.topology.TopologicalPoiQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(Raids.class)
public class RaidsPoiMixin {
    @WrapOperation(
            method = "createOrExtendRaid",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;getInRange(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/stream/Stream;"
            )
    )
    private Stream<PoiRecord> findRaidVillagePoisAcrossSeams(
            PoiManager poiManager,
            Predicate<Holder<PoiType>> type,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy,
            Operation<Stream<PoiRecord>> original,
            @Local ServerLevel level) {
        return TopologicalPoiQueries.recordsInRange(level, type, center, radius, occupancy);
    }
}
