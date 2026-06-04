package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MapItem.class)
public class MapItemMixin {
    @WrapOperation(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D")
    )
    private double useMapCenterNearestPlayerX(
            Entity player,
            Operation<Double> original,
            @Local(argsOnly = true) Level level,
            @Local(argsOnly = true) MapItemSavedData data) {
        return nearestMapAlias(level, data, original.call(player), data.centerX);
    }

    @WrapOperation(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D")
    )
    private double useMapCenterNearestPlayerZ(
            Entity player,
            Operation<Double> original,
            @Local(argsOnly = true) Level level,
            @Local(argsOnly = true) MapItemSavedData data) {
        return nearestMapAlias(level, data, original.call(player), data.centerZ);
    }

    private static double nearestMapAlias(Level level, MapItemSavedData data, double coordinate, int center) {
        if (!level.dimension().equals(data.dimension) || !DimensionTiling.forLevel(level).enabled()) {
            return coordinate;
        }
        return CoordUtil.virtualBlock(level, CoordUtil.wrapBlock(level, coordinate), center);
    }
}
