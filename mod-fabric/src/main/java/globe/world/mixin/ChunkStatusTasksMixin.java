package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.DimensionTiling;
import globe.world.util.ForcedProgressionStructures;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Mixin(ChunkStatusTasks.class)
public class ChunkStatusTasksMixin {
    @WrapOperation(
            method = "generateStructureStarts",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;createStructures(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/resources/ResourceKey;)V"
            )
    )
    private static void createStructuresWithDimensionTiling(
            ChunkGenerator generator,
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState state,
            StructureManager structureManager,
            ChunkAccess centerChunk,
            StructureTemplateManager structureTemplateManager,
            ResourceKey<Level> levelKey,
            Operation<Void> original,
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> chunks,
            ChunkAccess chunk) {
        runWithDimensionTiling(
                context.level(),
                () -> {
                    original.call(generator, registryAccess, state, structureManager, centerChunk, structureTemplateManager, levelKey);
                    ForcedProgressionStructures.maybeForceOverworldStronghold(
                            context.level(),
                            registryAccess,
                            state,
                            structureManager,
                            centerChunk,
                            generator,
                            structureTemplateManager,
                            levelKey
                    );
                    ForcedProgressionStructures.maybeForceNetherProgressionStructures(
                            context.level(),
                            registryAccess,
                            state,
                            structureManager,
                            centerChunk,
                            generator,
                            structureTemplateManager,
                            levelKey
                    );
                }
        );
    }

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

    private static void runWithDimensionTiling(ServerLevel level, Runnable action) {
        DimensionTiling.runWith(DimensionTiling.forLevel(level), action);
    }
}
