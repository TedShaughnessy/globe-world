package globe.world.mixin;

import globe.world.topology.TileGeometry;
import globe.world.util.DimensionTiling;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Structure.GenerationContext.class)
public class StructureGenerationContextMixin {
    @ModifyArgs(
            method = "makeRandom",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"
            )
    )
    private static void canonicalizeStructureGenerationSeed(Args args) {
        ChunkPos canonical = TileGeometry.create(DimensionTiling.currentOrOverworld())
                .canonicalChunk(args.get(1), args.get(2));
        args.set(1, canonical.x());
        args.set(2, canonical.z());
    }
}
