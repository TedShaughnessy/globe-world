package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.topology.TopologicalPoiQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(targets = "net.minecraft.world.entity.raid.Raider$RaiderMoveThroughVillageGoal")
public class RaiderPoiMixin {
    @WrapOperation(
            method = "hasSuitablePoi",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;getRandom(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;)Ljava/util/Optional;"
            )
    )
    private Optional<BlockPos> findRaidHomePoiAcrossSeams(
            PoiManager poiManager,
            Predicate<Holder<PoiType>> type,
            Predicate<BlockPos> filter,
            PoiManager.Occupancy occupancy,
            BlockPos center,
            int radius,
            RandomSource random,
            Operation<Optional<BlockPos>> original,
            @Local ServerLevel level) {
        return TopologicalPoiQueries.getRandom(level, type, filter, occupancy, center, radius, random);
    }
}
