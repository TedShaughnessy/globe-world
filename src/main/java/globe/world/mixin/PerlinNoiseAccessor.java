package globe.world.mixin;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PerlinNoise.class)
public interface PerlinNoiseAccessor {
    @Accessor("noiseLevels")
    ImprovedNoise[] globeWorld$noiseLevels();

    @Accessor("amplitudes")
    DoubleList globeWorld$amplitudes();

    @Accessor("lowestFreqInputFactor")
    double globeWorld$lowestFreqInputFactor();

    @Accessor("lowestFreqValueFactor")
    double globeWorld$lowestFreqValueFactor();
}
