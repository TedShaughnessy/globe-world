package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.ProjectileAliasUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ThrownSplashPotion.class)
public class ThrownSplashPotionAliasEffectMixin {
    @WrapOperation(
            method = "onHitAsPotion",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<LivingEntity> getWrappedSplashEffectEntities(
            Level level,
            Class<LivingEntity> entityClass,
            AABB effectAabb,
            Operation<List<LivingEntity>> original) {
        List<LivingEntity> vanillaEntities = original.call(level, entityClass, effectAabb);
        return ProjectileAliasUtil.addWrappedEntitiesOfClass(level, entityClass, effectAabb, vanillaEntities);
    }

    @WrapOperation(
            method = "onHitAsPotion",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/AABB;distanceToSqr(Lnet/minecraft/world/phys/AABB;)D"
            )
    )
    private double measureWrappedSplashDistance(AABB potionAabb, AABB entityBox, Operation<Double> original) {
        ThrownSplashPotion potion = (ThrownSplashPotion)(Object)this;
        AABB aliasBox = ProjectileAliasUtil.nearestAliasAabb(potion.level(), potionAabb, entityBox);
        return original.call(potionAabb, aliasBox);
    }
}
