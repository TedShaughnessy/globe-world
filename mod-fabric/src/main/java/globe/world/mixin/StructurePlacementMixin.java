package globe.world.mixin;

import globe.world.topology.TileGeometry;
import globe.world.util.DimensionTiling;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(StructurePlacement.class)
public class StructurePlacementMixin {
    @Inject(method = "isStructureChunk", at = @At("HEAD"), cancellable = true)
    private void checkPeriodicStructureChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ, CallbackInfoReturnable<Boolean> cir) {
        ChunkPos canonical = canonicalChunk(sourceX, sourceZ);
        if (canonical.x() != sourceX || canonical.z() != sourceZ) {
            cir.setReturnValue(((StructurePlacement) (Object) this).isStructureChunk(
                    state,
                    canonical.x(),
                    canonical.z()));
        }
    }

    @ModifyArgs(
            method = "probabilityReducer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureWithSalt(JIII)V"
            )
    )
    private static void canonicalizeProbabilityReducerSeed(Args args) {
        canonicalize(args, 2, 3);
    }

    @ModifyArgs(
            method = "legacyProbabilityReducerWithDouble",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"
            )
    )
    private static void canonicalizeLegacyProbabilitySeed(Args args) {
        canonicalize(args, 1, 2);
    }

    @ModifyArgs(
            method = "legacyArbitrarySaltProbabilityReducer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureWithSalt(JIII)V"
            )
    )
    private static void canonicalizeLegacyArbitrarySaltSeed(Args args) {
        canonicalize(args, 1, 2);
    }

    private static void canonicalize(Args args, int xIndex, int zIndex) {
        ChunkPos canonical = canonicalChunk(args.get(xIndex), args.get(zIndex));
        args.set(xIndex, canonical.x());
        args.set(zIndex, canonical.z());
    }

    private static ChunkPos canonicalChunk(int x, int z) {
        return TileGeometry.create(DimensionTiling.currentOrOverworld()).canonicalChunk(x, z);
    }
}
