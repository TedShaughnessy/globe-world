package globe.world.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
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
        TopologyContext topology = TopologyContexts.forLevel(level);
        if (target == null
                || owner == null
                || !globeWorld$usesNearestAlias(compassTarget)
                || !target.dimension().equals(level.dimension())
                || !topology.enabled()) {
            return target;
        }

        BlockPos pos = target.pos();
        Vec3 targetCenter = Vec3.atCenterOf(topology.canonicalBlock(pos));
        Vec3 visibleCenter = topology.virtualBlockForViewer(targetCenter, owner.position());
        return GlobalPos.of(target.dimension(), BlockPos.containing(visibleCenter));
    }

    private static boolean globeWorld$usesNearestAlias(CompassAngleState.CompassTarget compassTarget) {
        return compassTarget == CompassAngleState.CompassTarget.LODESTONE
                || compassTarget == CompassAngleState.CompassTarget.RECOVERY
                || compassTarget == CompassAngleState.CompassTarget.SPAWN;
    }
}
