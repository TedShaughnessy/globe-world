package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.ProjectileAliasUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;
import java.util.function.Predicate;

@Mixin(AbstractArrow.class)
public class AbstractArrowAliasCollisionMixin {
    @WrapOperation(
            method = "findHitEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getManyEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Z)Ljava/util/Collection;"
            )
    )
    private Collection<EntityHitResult> addWrappedEntityHits(
            Level level,
            Entity source,
            Vec3 from,
            Vec3 to,
            AABB targetSearchArea,
            Predicate<Entity> matching,
            boolean includeFromEntity,
            Operation<Collection<EntityHitResult>> original) {
        Collection<EntityHitResult> vanillaHits = original.call(
                level,
                source,
                from,
                to,
                targetSearchArea,
                matching,
                includeFromEntity);
        return ProjectileAliasUtil.addWrappedEntityHits(
                level,
                source,
                from,
                to,
                targetSearchArea,
                matching,
                vanillaHits,
                ClipContext.Block.COLLIDER,
                includeFromEntity);
    }
}
