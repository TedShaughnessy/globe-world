package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataMixin {
    @Shadow @Final public int centerX;
    @Shadow @Final public int centerZ;

    @WrapOperation(
        method = "tickCarriedBy",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;addDecoration(Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/LevelAccessor;Ljava/lang/String;DDDLnet/minecraft/network/chat/Component;)V"
        )
    )
    private void useMapCenterNearestPlayerDecoration(
            MapItemSavedData data,
            Holder<MapDecorationType> type,
            LevelAccessor level,
            String key,
            double xPos,
            double zPos,
            double yRot,
            Component name,
            Operation<Void> original) {
        if (isPlayerDecoration(type) && level instanceof Level realLevel && DimensionTiling.forLevel(realLevel).enabled()) {
            xPos = CoordUtil.virtualBlock(realLevel, CoordUtil.wrapBlock(realLevel, xPos), this.centerX);
            zPos = CoordUtil.virtualBlock(realLevel, CoordUtil.wrapBlock(realLevel, zPos), this.centerZ);
        }

        original.call(data, type, level, key, xPos, zPos, yRot, name);
    }

    private static boolean isPlayerDecoration(Holder<MapDecorationType> type) {
        return type == MapDecorationTypes.PLAYER || type.equals(MapDecorationTypes.PLAYER);
    }
}
