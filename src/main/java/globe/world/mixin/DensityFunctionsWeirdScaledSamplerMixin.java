package globe.world.mixin;

import globe.world.util.PeriodicNoiseUtil;
import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$WeirdScaledSampler")
public abstract class DensityFunctionsWeirdScaledSamplerMixin {
    @Unique
    private static Method globeWorld$rarityValueMapperMethod;

    @Unique
    private static Field globeWorld$rarityMapperField;

    @Shadow
    public abstract DensityFunction.NoiseHolder noise();

    @Inject(method = "transform", at = @At("HEAD"), cancellable = true)
    private void samplePeriodicWeirdScaledNoise(
            DensityFunction.FunctionContext context,
            double input,
            CallbackInfoReturnable<Double> cir) {
        double rarity = globeWorld$rarity(input);
        DensityFunction.NoiseHolder noise = this.noise();
        double value = PeriodicNoiseUtil.samplePlane(
                context.blockX(),
                context.blockZ(),
                1.0 / rarity,
                (x, z) -> rarity * Math.abs(noise.getValue(x, context.blockY() / rarity, z))
        );
        cir.setReturnValue(value);
    }

    @Unique
    private double globeWorld$rarity(double input) {
        try {
            Method method = globeWorld$rarityValueMapperMethod;
            if (method == null) {
                method = this.getClass().getDeclaredMethod("rarityValueMapper");
                method.setAccessible(true);
                globeWorld$rarityValueMapperMethod = method;
            }

            Object rarityValueMapper = method.invoke(this);
            Field field = globeWorld$rarityMapperField;
            if (field == null) {
                field = rarityValueMapper.getClass().getDeclaredField("mapper");
                field.setAccessible(true);
                globeWorld$rarityMapperField = field;
            }

            return ((Double2DoubleFunction) field.get(rarityValueMapper)).get(input);
        } catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to read WeirdScaledSampler rarity mapper", e);
        }
    }
}
