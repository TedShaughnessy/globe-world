package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.DamageAliasUtil;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityDamageSourceAliasMixin {
    @WrapOperation(
            method = {
                    "hurtServer",
                    "applyItemBlocking"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/DamageSource;getSourcePosition()Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 useNearestAliasDamageSourcePosition(DamageSource source, Operation<Vec3> original) {
        LivingEntity victim = (LivingEntity)(Object)this;
        return DamageAliasUtil.sourcePositionForVictim(victim, source, original.call(source));
    }
}
