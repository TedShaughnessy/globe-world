package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.GlobeLocalDaylight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mob.class)
public class MobLocalDaylightMixin {
    @WrapOperation(
        method = "isSunBurnTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/attribute/EnvironmentAttributeSystem;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;Lnet/minecraft/world/phys/Vec3;)Ljava/lang/Object;"
        )
    )
    private Object useLocalMonstersBurnPredicate(
            EnvironmentAttributeSystem attributes,
            EnvironmentAttribute<?> attribute,
            Vec3 pos,
            Operation<Object> original) {
        Mob mob = (Mob) (Object) this;
        if (attribute == EnvironmentAttributes.MONSTERS_BURN && GlobeLocalDaylight.enabled(mob.level())) {
            return GlobeLocalDaylight.monstersBurn(mob.level(), pos);
        }

        return original.call(attributes, attribute, pos);
    }

    @WrapOperation(
        method = "isSunBurnTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;getLightLevelDependentMagicValue()F"
        )
    )
    private float useLocalBrightnessForSunBurn(Mob mob, Operation<Float> original) {
        Level level = mob.level();
        if (!GlobeLocalDaylight.enabled(level)) {
            return original.call(mob);
        }

        BlockPos pos = BlockPos.containing(mob.getX(), mob.getEyeY(), mob.getZ());
        return GlobeLocalDaylight.getLightLevelDependentMagicValue(level, pos);
    }
}
