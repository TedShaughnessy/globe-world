package globe.world.mixin;

import globe.world.topology.TopologicalRaycasts;
import globe.world.util.DimensionTiling;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(ProjectileUtil.class)
public class ProjectileUtilTopologicalMoveMixin {
    @Inject(
            method = "getHitResultOnMoveVector(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void useTopologicalMoveVectorHitResult(
            Entity source,
            Predicate<Entity> matching,
            CallbackInfoReturnable<HitResult> cir) {
        if (!shouldUseTopologicalMove(source)) {
            return;
        }
        cir.setReturnValue(topologicalMoveVectorHitResult(source, matching, ClipContext.Block.COLLIDER));
    }

    @Inject(
            method = "getHitResultOnMoveVector(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;Lnet/minecraft/world/level/ClipContext$Block;)Lnet/minecraft/world/phys/HitResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void useTopologicalMoveVectorHitResult(
            Entity source,
            Predicate<Entity> matching,
            ClipContext.Block clipType,
            CallbackInfoReturnable<HitResult> cir) {
        if (!shouldUseTopologicalMove(source)) {
            return;
        }
        cir.setReturnValue(topologicalMoveVectorHitResult(source, matching, clipType));
    }

    private static boolean shouldUseTopologicalMove(Entity source) {
        return !source.level().isClientSide() && DimensionTiling.forLevel(source.level()).enabled();
    }

    private static HitResult topologicalMoveVectorHitResult(
            Entity source,
            Predicate<Entity> matching,
            ClipContext.Block clipType) {
        Vec3 nextPosition = source.position().add(source.getDeltaMovement());
        return TopologicalRaycasts.topologicalProjectileMove(source, nextPosition, matching, clipType).firstVisibleHit();
    }
}
