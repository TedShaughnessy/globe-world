package globe.world.mixin;

import globe.world.util.CoordUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BiomeFilter.class)
public class BiomeFilterMixin {

    @Redirect(
        method = "shouldPlace",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/WorldGenLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"
        )
    )
    private Holder<Biome> wrapBiomeLookup(WorldGenLevel level, BlockPos pos) {
        // getUncachedNoiseBiome samples BiomeSource directly (no chunk loading),
        // avoiding WorldGenRegion.getChunk() which throws for out-of-region chunks.
        return level.getUncachedNoiseBiome(
            QuartPos.fromBlock(CoordUtil.wrapBlock(level.getLevel(), pos.getX())),
            QuartPos.fromBlock(pos.getY()),
            QuartPos.fromBlock(CoordUtil.wrapBlock(level.getLevel(), pos.getZ()))
        );
    }
}
