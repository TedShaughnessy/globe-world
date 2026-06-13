package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.sugar.Local;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import globe.world.util.GlobeNaturalSpawning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
        if (!CoordUtil.wrapChunkPos(level.getLevel(), pos).equals(pos)) {
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
        original.call(mobCategory, level, canonicalChunk(level, chunk), CoordUtil.wrapBlockPos(level, start), extraTest, spawnCallback);
    }

    @ModifyVariable(
        method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 3
    )
    private static BlockPos wrapSpawnPosition(BlockPos pos) {
        return CoordUtil.wrapBlockPos(pos);
    }

    @WrapOperation(
        method = "spawnForChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getPos()Lnet/minecraft/world/level/ChunkPos;"
        )
    )
    private static ChunkPos wrapLocalMobCapChunk(LevelChunk chunk, Operation<ChunkPos> original) {
        return CoordUtil.wrapChunkPos(chunk.getLevel(), original.call(chunk));
    }

    @WrapOperation(
        method = "getRandomPosWithin(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/chunk/LevelChunk;)Lnet/minecraft/core/BlockPos;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getPos()Lnet/minecraft/world/level/ChunkPos;"
        )
    )
    private static ChunkPos wrapRandomSpawnChunk(LevelChunk chunk, Operation<ChunkPos> original) {
        return CoordUtil.wrapChunkPos(chunk.getLevel(), original.call(chunk));
    }

    @WrapOperation(
        method = "lambda$createState$0",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getPos()Lnet/minecraft/world/level/ChunkPos;"
        )
    )
    private static ChunkPos wrapCountedMobChunk(LevelChunk chunk, Operation<ChunkPos> original) {
        return CoordUtil.wrapChunkPos(chunk.getLevel(), original.call(chunk));
    }

    @ModifyVariable(
        method = "getRandomPosWithin(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/chunk/LevelChunk;)Lnet/minecraft/core/BlockPos;",
        at = @At("STORE"),
        index = 3
    )
    private static int wrapRandomSpawnX(int x) {
        return CoordUtil.wrapBlock(x);
    }

    @ModifyVariable(
        method = "getRandomPosWithin(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/chunk/LevelChunk;)Lnet/minecraft/core/BlockPos;",
        at = @At("STORE"),
        index = 4
    )
    private static int wrapRandomSpawnZ(int z) {
        return CoordUtil.wrapBlock(z);
    }

    @ModifyVariable(
        method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
        at = @At("STORE"),
        index = 13
    )
    private static int wrapSpawnCandidateX(int x) {
        return CoordUtil.wrapBlock(x);
    }

    @ModifyVariable(
        method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
        at = @At("STORE"),
        index = 14
    )
    private static int wrapSpawnCandidateZ(int z) {
        return CoordUtil.wrapBlock(z);
    }

    @WrapOperation(
        method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;distanceToSqr(DDD)D"
        )
    )
    private static double wrapSpawnPointDistance(Player player, double x, double y, double z, Operation<Double> original) {
        return CoordUtil.wrappedDistanceSqr(player.level(), player.getX(), player.getY(), player.getZ(), x, y, z);
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
        if (!DimensionTiling.forLevel(level).enabled()) {
            return original.call(spawnPos, candidate, distance);
        }
        return CoordUtil.wrappedDistanceSqr(
                level,
                spawnPos.getX() + 0.5,
                spawnPos.getY() + 0.5,
                spawnPos.getZ() + 0.5,
                candidate.x(),
                candidate.y(),
                candidate.z()) < distance * distance;
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
        int wx = CoordUtil.wrapChunk(level, pos.x());
        int wz = CoordUtil.wrapChunk(level, pos.z());
        if (wx == pos.x() && wz == pos.z() && chunk instanceof LevelChunk levelChunk) {
            return levelChunk;
        }
        return level.getChunk(wx, wz);
    }
}
