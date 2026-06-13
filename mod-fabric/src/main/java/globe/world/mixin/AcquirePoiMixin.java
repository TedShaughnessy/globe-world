package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import globe.world.topology.TopologicalPoiQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(AcquirePoi.class)
public class AcquirePoiMixin {
    @WrapOperation(
            method = "lambda$create$3",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;findAllClosestFirstWithType(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/stream/Stream;"
            )
    )
    private static Stream<Pair<Holder<PoiType>, BlockPos>> findReachablePoiCandidatesAcrossSeams(
            PoiManager poiManager,
            Predicate<Holder<PoiType>> type,
            Predicate<BlockPos> filter,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy,
            Operation<Stream<Pair<Holder<PoiType>, BlockPos>>> original,
            @Local(argsOnly = true) ServerLevel level) {
        return TopologicalPoiQueries.findAllClosestFirstWithType(level, type, filter, center, radius, occupancy);
    }

    @WrapOperation(
            method = "lambda$create$7",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;take(Ljava/util/function/Predicate;Ljava/util/function/BiPredicate;Lnet/minecraft/core/BlockPos;I)Ljava/util/Optional;"
            )
    )
    private static Optional<BlockPos> reserveCanonicalPoiAcrossSeams(
            PoiManager poiManager,
            Predicate<Holder<PoiType>> type,
            BiPredicate<Holder<PoiType>, BlockPos> filter,
            BlockPos center,
            int radius,
            Operation<Optional<BlockPos>> original,
            @Local(argsOnly = true) ServerLevel level) {
        return TopologicalPoiQueries.take(level, type, filter, center, radius);
    }
}
