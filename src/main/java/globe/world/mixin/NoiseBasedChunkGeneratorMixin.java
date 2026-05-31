package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

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

    @ModifyArg(
            method = "applyCarvers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"
            ),
            index = 1
    )
    private int wrapCarverSeedX(int sourceX) {
        return CoordUtil.wrapChunk(sourceX);
    }

    @ModifyArg(
            method = "applyCarvers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"
            ),
            index = 2
    )
    private int wrapCarverSeedZ(int sourceZ) {
        return CoordUtil.wrapChunk(sourceZ);
    }
}
