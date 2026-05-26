package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(method = "applyCarvers", at = @At("HEAD"))
    private void pushCarverTilingContext(
            WorldGenRegion region,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            CallbackInfo ci) {
        DimensionTiling.push(DimensionTiling.forLevel(region.getLevel()));
    }

    @Inject(method = "applyCarvers", at = @At("RETURN"))
    private void clearCarverTilingContext(
            WorldGenRegion region,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            CallbackInfo ci) {
        DimensionTiling.clear();
    }

    @Inject(method = "buildSurface", at = @At("HEAD"))
    private void pushSurfaceTilingContext(
            WorldGenRegion level,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess protoChunk,
            CallbackInfo ci) {
        DimensionTiling.push(DimensionTiling.forLevel(level.getLevel()));
    }

    @Inject(method = "buildSurface", at = @At("RETURN"))
    private void clearSurfaceTilingContext(
            WorldGenRegion level,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess protoChunk,
            CallbackInfo ci) {
        DimensionTiling.clear();
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
