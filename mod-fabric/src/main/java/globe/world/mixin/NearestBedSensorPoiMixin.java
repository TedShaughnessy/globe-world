package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import globe.world.topology.TopologicalPoiQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.sensing.NearestBedSensor;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(NearestBedSensor.class)
public class NearestBedSensorPoiMixin {
    @WrapOperation(
            method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;findAllWithType(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/stream/Stream;"
            )
    )
    private Stream<Pair<Holder<PoiType>, BlockPos>> findBedsAcrossSeams(
            PoiManager poiManager,
            Predicate<Holder<PoiType>> type,
            Predicate<BlockPos> filter,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy,
            Operation<Stream<Pair<Holder<PoiType>, BlockPos>>> original,
            @Local(argsOnly = true) ServerLevel level) {
        return TopologicalPoiQueries.findAllWithType(level, type, filter, center, radius, occupancy);
    }
}
