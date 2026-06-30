package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.config.GameplaySettings;
import globe.world.config.GlobeConfig;
import globe.world.topology.TopologyContexts;
import globe.world.util.DimensionTiling;
import globe.world.util.GlobeNaturalSpawning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {
    @WrapMethod(method = "getRandomPosWithin(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/chunk/LevelChunk;)Lnet/minecraft/core/BlockPos;")
    private static BlockPos getRandomPosWithinWithTilingContext(
            Level level,
            LevelChunk chunk,
            Operation<BlockPos> original) {
        return DimensionTiling.with(DimensionTiling.forLevel(level), () -> original.call(level, chunk));
    }

    @WrapMethod(method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V")
    private static void spawnCategoryForPositionWithTilingContext(
            net.minecraft.world.entity.MobCategory mobCategory,
            ServerLevel level,
            ChunkAccess chunk,
            BlockPos start,
            NaturalSpawner.SpawnPredicate extraTest,
            NaturalSpawner.AfterSpawnCallback spawnCallback,
            Operation<Void> original) {
        DimensionTiling.runWith(
                DimensionTiling.forLevel(level),
                () -> original.call(mobCategory, level, chunk, start, extraTest, spawnCallback)
        );
    }

    @Inject(
        method = "spawnMobsForChunkGeneration",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void skipAliasChunkGenerationSpawns(
            net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome,
            ChunkPos pos,
            net.minecraft.util.RandomSource random,
            CallbackInfo ci) {
        if (!TopologyContexts.forLevel(level.getLevel()).isCanonical(pos)) {
            ci.cancel();
        }
    }

    @WrapOperation(
        method = "spawnCategoryForChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/NaturalSpawner;getRandomPosWithin(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/chunk/LevelChunk;)Lnet/minecraft/core/BlockPos;"
        )
    )
    private static BlockPos getRandomPosWithinCanonicalChunk(
            Level level,
            LevelChunk chunk,
            Operation<BlockPos> original) {
        return original.call(level, canonicalChunk((ServerLevel) level, chunk));
    }

    @WrapOperation(
        method = "spawnCategoryForChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/NaturalSpawner;spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V"
        )
    )
    private static void spawnCategoryForCanonicalPosition(
            net.minecraft.world.entity.MobCategory mobCategory,
            ServerLevel level,
            ChunkAccess chunk,
            BlockPos start,
            NaturalSpawner.SpawnPredicate extraTest,
            NaturalSpawner.AfterSpawnCallback spawnCallback,
            Operation<Void> original) {
        original.call(mobCategory, level, canonicalChunk(level, chunk), TopologyContexts.forLevel(level).canonicalBlock(start), extraTest, spawnCallback);
    }

    @ModifyVariable(
        method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 3
    )
    private static BlockPos wrapSpawnPosition(BlockPos pos) {
        return globe.world.topology.TileGeometry.create(DimensionTiling.currentOrOverworld())
                .canonicalBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    @WrapOperation(
        method = "spawnForChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getPos()Lnet/minecraft/world/level/ChunkPos;"
        )
    )
    private static ChunkPos wrapLocalMobCapChunk(LevelChunk chunk, Operation<ChunkPos> original) {
        return TopologyContexts.forLevel(chunk.getLevel()).canonicalChunk(original.call(chunk));
    }

    @WrapOperation(
        method = "getRandomPosWithin(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/chunk/LevelChunk;)Lnet/minecraft/core/BlockPos;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getPos()Lnet/minecraft/world/level/ChunkPos;"
        )
    )
    private static ChunkPos wrapRandomSpawnChunk(LevelChunk chunk, Operation<ChunkPos> original) {
        return TopologyContexts.forLevel(chunk.getLevel()).canonicalChunk(original.call(chunk));
    }

    @WrapOperation(
        method = "lambda$createState$0",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getPos()Lnet/minecraft/world/level/ChunkPos;"
        )
    )
    private static ChunkPos wrapCountedMobChunk(LevelChunk chunk, Operation<ChunkPos> original) {
        return TopologyContexts.forLevel(chunk.getLevel()).canonicalChunk(original.call(chunk));
    }

    @WrapOperation(
        method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;set(III)Lnet/minecraft/core/BlockPos$MutableBlockPos;"
        )
    )
    private static BlockPos.MutableBlockPos canonicalizeSpawnCandidate(
            BlockPos.MutableBlockPos pos,
            int x,
            int y,
            int z,
            Operation<BlockPos.MutableBlockPos> original,
            @Local(argsOnly = true) ServerLevel level) {
        BlockPos canonical = TopologyContexts.forLevel(level).canonicalBlock(x, y, z);
        return original.call(pos, canonical.getX(), canonical.getY(), canonical.getZ());
    }

    @WrapOperation(
        method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;distanceToSqr(DDD)D"
        )
    )
    private static double wrapSpawnPointDistance(Player player, double x, double y, double z, Operation<Double> original) {
        return TopologyContexts.forLevel(player.level()).wrappedDistanceSqr(
                player.position(),
                new Vec3(x, y, z));
    }

    @ModifyConstant(
            method = "isRightDistanceToPlayerAndSpawnPoint",
            constant = @Constant(doubleValue = 576.0D)
    )
    private static double useConfiguredPlayerMobSpawnExclusion(double vanillaDistanceSqr) {
        int exclusionBlocks = GlobeConfig.playerMobSpawnExclusionBlocks();
        if (exclusionBlocks == GameplaySettings.PLAYER_MOB_SPAWN_EXCLUSION_DEFAULT_BLOCKS) {
            return vanillaDistanceSqr;
        }
        return (double) exclusionBlocks * (double) exclusionBlocks;
    }

    @WrapOperation(
        method = "isRightDistanceToPlayerAndSpawnPoint",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private static boolean useWrappedDistanceToWorldSpawn(
            BlockPos spawnPos,
            Position candidate,
            double distance,
            Operation<Boolean> original,
            @Local(argsOnly = true) ServerLevel level) {
        if (GlobeConfig.allowMobsAtWorldSpawn()) {
            return false;
        }
        if (!DimensionTiling.forLevel(level).enabled()) {
            return original.call(spawnPos, candidate, distance);
        }
        return TopologyContexts.forLevel(level).wrappedDistanceSqr(
                Vec3.atCenterOf(spawnPos),
                new Vec3(candidate.x(), candidate.y(), candidate.z())) < distance * distance;
    }

    @WrapOperation(
        method = "isRightDistanceToPlayerAndSpawnPoint",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;canSpawnEntitiesInChunk(Lnet/minecraft/world/level/ChunkPos;)Z"
        )
    )
    private static boolean canSpawnPackMemberInViewerAliasChunk(
            ServerLevel level,
            ChunkPos pos,
            Operation<Boolean> original) {
        return GlobeNaturalSpawning.canSpawnEntitiesInChunkOrViewerAlias(
                level,
                pos,
                candidate -> original.call(level, candidate));
    }

    private static LevelChunk canonicalChunk(ServerLevel level, ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        ChunkPos canonical = TopologyContexts.forLevel(level).canonicalChunk(pos);
        if (canonical.equals(pos) && chunk instanceof LevelChunk levelChunk) {
            return levelChunk;
        }
        return level.getChunk(canonical.x(), canonical.z());
    }
}
