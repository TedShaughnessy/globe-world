package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.topology.TopologicalPoiQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.GoToClosestVillage;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.ToDoubleFunction;

@Mixin(GoToClosestVillage.class)
public class GoToClosestVillagePoiMixin {
    private static final ThreadLocal<ServerLevel> GLOBE_WORLD_LEVEL = new ThreadLocal<>();

    @WrapOperation(
            method = "lambda$create$2",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;sectionsToVillage(Lnet/minecraft/core/SectionPos;)I"
            )
    )
    private static int useTopologicalVillageDistance(
            PoiManager poiManager,
            SectionPos sectionPos,
            Operation<Integer> original,
            @Local(argsOnly = true) ServerLevel level) {
        return TopologicalPoiQueries.sectionsToVillage(level, sectionPos);
    }

    @WrapOperation(
            method = "lambda$create$2",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/util/LandRandomPos;getPos(Lnet/minecraft/world/entity/PathfinderMob;IILjava/util/function/ToDoubleFunction;)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private static Vec3 enterTopologicalVillageScoring(
            PathfinderMob mob,
            int xzRange,
            int yRange,
            ToDoubleFunction<BlockPos> scorer,
            Operation<Vec3> original,
            @Local(argsOnly = true) ServerLevel level) {
        ServerLevel previous = GLOBE_WORLD_LEVEL.get();
        GLOBE_WORLD_LEVEL.set(level);
        try {
            return original.call(mob, xzRange, yRange, scorer);
        } finally {
            if (previous == null) {
                GLOBE_WORLD_LEVEL.remove();
            } else {
                GLOBE_WORLD_LEVEL.set(previous);
            }
        }
    }

    @WrapOperation(
            method = "lambda$create$3",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;sectionsToVillage(Lnet/minecraft/core/SectionPos;)I"
            )
    )
    private static int scoreTopologicalVillageDistance(
            PoiManager poiManager,
            SectionPos sectionPos,
            Operation<Integer> original) {
        ServerLevel level = GLOBE_WORLD_LEVEL.get();
        return level == null
                ? original.call(poiManager, sectionPos)
                : TopologicalPoiQueries.sectionsToVillage(level, sectionPos);
    }
}
