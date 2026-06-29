package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import globe.world.topology.TileGeometry;
import globe.world.util.DimensionTiling;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorMixin {
    @WrapOperation(
            method = "createBiomes",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<ChunkAccess> preserveBiomeTilingContext(
            Supplier<ChunkAccess> supplier,
            Executor executor,
            Operation<CompletableFuture<ChunkAccess>> original) {
        DimensionTiling tiling = DimensionTiling.currentOrOverworld();
        return original.call((Supplier<ChunkAccess>) () -> DimensionTiling.with(tiling, supplier), executor);
    }

    @WrapOperation(
            method = "fillFromNoise",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<ChunkAccess> preserveNoiseTilingContext(
            Supplier<ChunkAccess> supplier,
            Executor executor,
            Operation<CompletableFuture<ChunkAccess>> original) {
        DimensionTiling tiling = DimensionTiling.currentOrOverworld();
        return original.call((Supplier<ChunkAccess>) () -> DimensionTiling.with(tiling, supplier), executor);
    }

    @WrapMethod(method = "applyCarvers(Lnet/minecraft/server/level/WorldGenRegion;JLnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)V")
    private void applyCarversWithTilingContext(
            WorldGenRegion region,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            Operation<Void> original) {
        DimensionTiling.runWith(
                DimensionTiling.forLevel(((WorldGenRegionAccessor) region).globeWorld$level()),
                () -> original.call(region, seed, randomState, biomeManager, structureManager, chunk)
        );
    }

    @WrapMethod(method = "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V")
    private void buildSurfaceWithTilingContext(
            WorldGenRegion level,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess protoChunk,
            Operation<Void> original) {
        DimensionTiling.runWith(
                DimensionTiling.forLevel(((WorldGenRegionAccessor) level).globeWorld$level()),
                () -> original.call(level, structureManager, randomState, protoChunk)
        );
    }

    @ModifyArgs(
            method = "applyCarvers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"
            )
    )
    private void canonicalizeCarverSeed(Args args) {
        ChunkPos canonical = TileGeometry.create(DimensionTiling.currentOrOverworld())
                .canonicalChunk(args.get(1), args.get(2));
        args.set(1, canonical.x());
        args.set(2, canonical.z());
    }
}
