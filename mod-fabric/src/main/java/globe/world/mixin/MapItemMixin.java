package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
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
        return nearestMapAlias(level, data, player).x();
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
        return nearestMapAlias(level, data, player).z();
    }

    @WrapOperation(
        method = "update",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;checkBanners(Lnet/minecraft/world/level/BlockGetter;II)V"
        )
    )
    private void checkCanonicalBannerColumn(
            MapItemSavedData data,
            net.minecraft.world.level.BlockGetter levelReader,
            int x,
            int z,
            Operation<Void> original,
            @Local(argsOnly = true) Level level) {
        BlockPos canonical = TopologyContexts.forLevel(level).canonicalBlock(x, 0, z);
        original.call(data, levelReader, canonical.getX(), canonical.getZ());
    }

    private static Vec3 nearestMapAlias(Level level, MapItemSavedData data, Entity player) {
        if (!level.dimension().equals(data.dimension)) {
            return player.position();
        }
        TopologyContext topology = TopologyContexts.forLevel(level);
        Vec3 canonical = topology.canonicalBlock(player.position());
        return topology.virtualBlockForViewer(
                canonical,
                new Vec3(data.centerX + 0.5D, canonical.y(), data.centerZ + 0.5D));
    }
}
