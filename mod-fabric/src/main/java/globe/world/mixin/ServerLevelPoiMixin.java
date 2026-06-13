package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologicalPoiQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(ServerLevel.class)
public class ServerLevelPoiMixin {
    @WrapOperation(
            method = "findLightningRod",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;findClosest(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;"
            )
    )
    private Optional<BlockPos> findLightningRodAcrossSeams(
            PoiManager poiManager,
            Predicate<Holder<PoiType>> type,
            Predicate<BlockPos> filter,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy,
            Operation<Optional<BlockPos>> original) {
        return TopologicalPoiQueries.findClosest((ServerLevel)(Object)this, type, filter, center, radius, occupancy);
    }

    @Inject(method = "sectionsToVillage", at = @At("HEAD"), cancellable = true)
    private void useTopologicalVillageSectionDistance(SectionPos pos, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(TopologicalPoiQueries.sectionsToVillage((ServerLevel)(Object)this, pos));
    }
}
