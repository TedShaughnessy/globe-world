package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.DimensionTiling;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Mixin(ChunkStatusTasks.class)
public class ChunkStatusTasksMixin {
    @WrapOperation(
            method = "generateBiomes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;createBiomes(Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private static CompletableFuture<ChunkAccess> createBiomesWithDimensionTiling(
            ChunkGenerator generator,
            RandomState randomState,
            Blender blender,
            StructureManager structureManager,
            ChunkAccess protoChunk,
            Operation<CompletableFuture<ChunkAccess>> original,
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> chunks,
            ChunkAccess chunk) {
        return withDimensionTiling(context.level(), () -> original.call(generator, randomState, blender, structureManager, protoChunk));
    }

    @WrapOperation(
            method = "generateNoise",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;fillFromNoise(Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private static CompletableFuture<ChunkAccess> fillFromNoiseWithDimensionTiling(
            ChunkGenerator generator,
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess centerChunk,
            Operation<CompletableFuture<ChunkAccess>> original,
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> chunks,
            ChunkAccess chunk) {
        return withDimensionTiling(context.level(), () -> original.call(generator, blender, randomState, structureManager, centerChunk));
    }

    private static <T> T withDimensionTiling(ServerLevel level, Supplier<T> action) {
        return DimensionTiling.with(DimensionTiling.forLevel(level), action);
    }
}
