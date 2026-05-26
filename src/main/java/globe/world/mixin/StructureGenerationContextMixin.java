package globe.world.mixin;

import globe.world.util.CoordUtil;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Structure.GenerationContext.class)
public class StructureGenerationContextMixin {
    @ModifyArg(
            method = "makeRandom",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"
            ),
            index = 1
    )
    private static int wrapStructureGenerationSeedX(int chunkX) {
        return CoordUtil.wrapChunk(chunkX);
    }

    @ModifyArg(
            method = "makeRandom",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"
            ),
            index = 2
    )
    private static int wrapStructureGenerationSeedZ(int chunkZ) {
        return CoordUtil.wrapChunk(chunkZ);
    }
}
