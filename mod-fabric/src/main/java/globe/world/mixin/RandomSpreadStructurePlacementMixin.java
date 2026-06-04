package globe.world.mixin;

import globe.world.util.CoordUtil;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RandomSpreadStructurePlacement.class)
public class RandomSpreadStructurePlacementMixin {
    @ModifyArg(
            method = "getPotentialStructureChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureWithSalt(JIII)V"
            ),
            index = 1
    )
    private int wrapRandomSpreadSeedX(int spacedGridX) {
        return CoordUtil.wrapChunk(spacedGridX);
    }

    @ModifyArg(
            method = "getPotentialStructureChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureWithSalt(JIII)V"
            ),
            index = 2
    )
    private int wrapRandomSpreadSeedZ(int spacedGridZ) {
        return CoordUtil.wrapChunk(spacedGridZ);
    }
}
