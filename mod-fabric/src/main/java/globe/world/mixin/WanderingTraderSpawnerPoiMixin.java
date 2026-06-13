package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.topology.TopologicalPoiQueries;
import globe.world.util.DimensionTiling;
import globe.world.util.GlobeSpawnFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(WanderingTraderSpawner.class)
public class WanderingTraderSpawnerPoiMixin {
    @Shadow @Final private RandomSource random;

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

    @WrapMethod(method = "findSpawnPositionNear")
    @Nullable
    private BlockPos findSpawnPositionAcrossSeams(
            LevelReader level,
            BlockPos referencePosition,
            int radius,
            Operation<BlockPos> original) {
        if (level instanceof Level actualLevel && DimensionTiling.forLevel(actualLevel).enabled()) {
            return GlobeSpawnFinder.findWanderingTraderSpawnPositionNear(level, referencePosition, radius, this.random);
        }
        return original.call(level, referencePosition, radius);
    }
}
