package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.topology.TopologicalPoiQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(WanderingTraderSpawner.class)
public class WanderingTraderSpawnerPoiMixin {
    @WrapOperation(
            method = "spawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;find(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;"
            )
    )
    private Optional<BlockPos> findMeetingPointAcrossSeams(
            PoiManager poiManager,
            Predicate<Holder<PoiType>> type,
            Predicate<BlockPos> filter,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy,
            Operation<Optional<BlockPos>> original,
            @Local(argsOnly = true) ServerLevel level) {
        return TopologicalPoiQueries.find(level, type, filter, center, radius, occupancy);
    }
}
