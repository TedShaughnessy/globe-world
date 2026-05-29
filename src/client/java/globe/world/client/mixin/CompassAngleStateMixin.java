package globe.world.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CompassAngleState.class)
public class CompassAngleStateMixin {
    @WrapOperation(
        method = "calculate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/properties/numeric/CompassAngleState$CompassTarget;get(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/ItemOwner;)Lnet/minecraft/core/GlobalPos;"
        )
    )
    private GlobalPos useNearestTargetAlias(
            CompassAngleState.CompassTarget compassTarget,
            ClientLevel level,
            ItemStack itemStack,
            ItemOwner owner,
            Operation<GlobalPos> original) {
        GlobalPos target = original.call(compassTarget, level, itemStack, owner);
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (target == null
                || owner == null
                || !globeWorld$usesNearestAlias(compassTarget)
                || target.dimension() != level.dimension()
                || !tiling.enabled()) {
            return target;
        }

        BlockPos pos = target.pos();
        double targetX = CoordUtil.virtualBlock(level, pos.getX() + 0.5, owner.position().x());
        double targetZ = CoordUtil.virtualBlock(level, pos.getZ() + 0.5, owner.position().z());
        return GlobalPos.of(target.dimension(), new BlockPos(Mth.floor(targetX), pos.getY(), Mth.floor(targetZ)));
    }

    private static boolean globeWorld$usesNearestAlias(CompassAngleState.CompassTarget compassTarget) {
        return compassTarget == CompassAngleState.CompassTarget.LODESTONE
                || compassTarget == CompassAngleState.CompassTarget.RECOVERY
                || compassTarget == CompassAngleState.CompassTarget.SPAWN;
    }
}
