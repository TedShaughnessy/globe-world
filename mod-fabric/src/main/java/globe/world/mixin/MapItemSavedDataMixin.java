package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TileGeometry;
import globe.world.util.DimensionTiling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataMixin {
    @Shadow @Final public int centerX;
    @Shadow @Final public int centerZ;
    @Shadow @Final public ResourceKey<Level> dimension;

    @WrapMethod(method = "addDecoration")
    private void useMapCenterNearestDecorationAlias(
            Holder<MapDecorationType> type,
            LevelAccessor level,
            String key,
            double xPos,
            double zPos,
            double yRot,
            Component name,
            Operation<Void> original) {
        TileGeometry geometry = TileGeometry.create(DimensionTiling.forDimension(this.dimension));
        if (geometry.enabled()) {
            Vec3 canonical = geometry.canonicalBlock(new Vec3(xPos, 0.0D, zPos));
            Vec3 visible = geometry.nearestAlias(
                    canonical,
                    new Vec3(this.centerX + 0.5D, 0.0D, this.centerZ + 0.5D));
            xPos = visible.x();
            zPos = visible.z();
        }

        original.call(type, level, key, xPos, zPos, yRot, name);
    }

    @WrapOperation(
            method = "toggleBanner",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getX()I"))
    private int useMapCenterNearestBannerX(
            BlockPos pos,
            Operation<Integer> original,
            LevelAccessor level,
            BlockPos toggledPos) {
        return nearestBannerAlias(toggledPos).getX();
    }

    @WrapOperation(
            method = "toggleBanner",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getZ()I"))
    private int useMapCenterNearestBannerZ(
            BlockPos pos,
            Operation<Integer> original,
            LevelAccessor level,
            BlockPos toggledPos) {
        return nearestBannerAlias(toggledPos).getZ();
    }

    private BlockPos nearestBannerAlias(BlockPos pos) {
        TileGeometry geometry = TileGeometry.create(DimensionTiling.forDimension(this.dimension));
        BlockPos canonical = geometry.canonicalBlock(pos.getX(), pos.getY(), pos.getZ());
        return geometry.nearestAlias(
                canonical,
                new Vec3(this.centerX + 0.5D, pos.getY(), this.centerZ + 0.5D));
    }
}
