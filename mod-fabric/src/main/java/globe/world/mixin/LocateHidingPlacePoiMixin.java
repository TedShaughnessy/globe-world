package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.topology.TopologicalPoiQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.behavior.LocateHidingPlace;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(LocateHidingPlace.class)
public class LocateHidingPlacePoiMixin {
    @WrapOperation(
            method = "lambda$create$2",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;find(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;"
            )
    )
    private static Optional<BlockPos> findNearbyHidingPlaceAcrossSeams(
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

    @WrapOperation(
            method = "lambda$create$6",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;getRandom(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;)Ljava/util/Optional;"
            )
    )
    private static Optional<BlockPos> findRandomHidingPlaceAcrossSeams(
            PoiManager poiManager,
            Predicate<Holder<PoiType>> type,
            Predicate<BlockPos> filter,
            PoiManager.Occupancy occupancy,
            BlockPos center,
            int radius,
            RandomSource random,
            Operation<Optional<BlockPos>> original,
            @Local(argsOnly = true) ServerLevel level) {
        return TopologicalPoiQueries.getRandom(level, type, filter, occupancy, center, radius, random);
    }
}
