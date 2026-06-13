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
import net.minecraft.world.entity.npc.CatSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

@Mixin(CatSpawner.class)
public class CatSpawnerPoiMixin {
    @WrapOperation(
            method = "spawnInVillage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;getCountInRange(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)J"
            )
    )
    private long countBedsAcrossSeams(
            PoiManager poiManager,
            Predicate<Holder<PoiType>> type,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy,
            Operation<Long> original,
            @Local(argsOnly = true) ServerLevel level) {
        return TopologicalPoiQueries.countInRange(level, type, center, radius, occupancy);
    }
}
