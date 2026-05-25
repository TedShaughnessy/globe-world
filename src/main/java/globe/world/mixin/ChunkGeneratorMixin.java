package globe.world.mixin;

import globe.world.util.CoordUtil;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"), cancellable = true)
    private void skipAliasBiomeDecoration(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager,
            CallbackInfo ci) {
        if (!isCanonical(chunk.getPos())) {
            ci.cancel();
        }
    }

    @Inject(method = "createStructures", at = @At("HEAD"), cancellable = true)
    private void skipAliasStructureStarts(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState state,
            StructureManager structureManager,
            ChunkAccess centerChunk,
            StructureTemplateManager structureTemplateManager,
            ResourceKey<Level> level,
            CallbackInfo ci) {
        if (!isCanonical(centerChunk.getPos())) {
            clearAliasStructures(centerChunk);
            ci.cancel();
        }
    }

    @Inject(method = "createReferences", at = @At("HEAD"), cancellable = true)
    private void skipAliasStructureReferences(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkAccess centerChunk,
            CallbackInfo ci) {
        if (!isCanonical(centerChunk.getPos())) {
            clearAliasStructures(centerChunk);
            ci.cancel();
        }
    }

    private static boolean isCanonical(ChunkPos pos) {
        return CoordUtil.wrapChunk(pos.x()) == pos.x() && CoordUtil.wrapChunk(pos.z()) == pos.z();
    }

    private static void clearAliasStructures(ChunkAccess chunk) {
        chunk.setAllStarts(Collections.emptyMap());
        chunk.setAllReferences(Collections.emptyMap());
    }
}
