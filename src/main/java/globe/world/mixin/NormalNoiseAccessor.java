package globe.world.mixin;

import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NormalNoise.class)
public interface NormalNoiseAccessor {
    @Accessor("first")
    PerlinNoise globeWorld$first();

    @Accessor("second")
    PerlinNoise globeWorld$second();

    @Accessor("valueFactor")
    double globeWorld$valueFactor();
}
