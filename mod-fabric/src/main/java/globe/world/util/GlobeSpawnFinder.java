package globe.world.util;

import globe.world.GlobeWorld;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

public final class GlobeSpawnFinder {
    private static final EntityDimensions PLAYER_DIMENSIONS = EntityType.PLAYER.getDimensions();
    private static final int EVENT_SPAWN_ATTEMPTS = 10;

    private GlobeSpawnFinder() {
    }

    public static CompletableFuture<Vec3> findSpawn(ServerLevel level, BlockPos spawnSuggestion) {
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (!tiling.enabled()) {
            return null;
        }

        BlockPos canonicalSuggestion = CoordUtil.wrapBlockPos(tiling, spawnSuggestion);
        if (!level.dimensionType().hasSkyLight() || level.getServer().getWorldData().getGameType() == GameType.ADVENTURE) {
            return CompletableFuture.completedFuture(fixupSpawnHeightInTile(level, canonicalSuggestion));
        }

        SpawnSearch search = new SpawnSearch(level, tiling, canonicalSuggestion);
        search.scheduleNext();
        return search.future;
    }

    @Nullable
    public static BlockPos findSpawnPosInChunk(ServerLevel level, ChunkPos chunkPos) {
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (!tiling.enabled()) {
            return null;
        }

        ChunkPos canonicalChunk = CoordUtil.wrapChunkPos(tiling, chunkPos);
        if (SharedConstants.debugVoidTerrain(canonicalChunk)) {
            return null;
        }

        return findDryLandInChunk(level, canonicalChunk);
    }

    public static BlockPos findInitialSpawnInCanonicalTile(ServerLevel level, ChunkPos spawnChunk) {
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (!tiling.enabled()) {
            return null;
        }

        BlockPos suggestion = CoordUtil.wrapBlockPos(tiling, spawnChunk.getWorldPosition().offset(8, 0, 8));
        CanonicalChunkSearch chunks = orderedCanonicalChunks(tiling, suggestion);
        for (ChunkPos chunk : chunks) {
            BlockPos pos = findDryLandInChunk(level, chunk);
            if (pos != null) {
                return pos;
            }
        }

        for (ChunkPos chunk : chunks) {
            BlockPos pos = findSafeSurfaceInChunk(level, chunk);
            if (pos != null) {
                return pos;
            }
        }

        return BlockPos.containing(lastResort(level, tiling));
    }

    public static LevelData.RespawnData canonicalRespawnData(ServerLevel level, LevelData.RespawnData respawnData) {
        BlockPos canonicalPos = CoordUtil.wrapBlockPos(level, respawnData.pos());
        if (canonicalPos.equals(respawnData.pos()) && respawnData.dimension().equals(level.dimension())) {
            return respawnData;
        }
        return LevelData.RespawnData.of(level.dimension(), canonicalPos, respawnData.yaw(), respawnData.pitch());
    }

    public static ServerPlayer.RespawnConfig canonicalRespawnConfig(ServerLevel level, ServerPlayer.RespawnConfig respawnConfig) {
        LevelData.RespawnData respawnData = canonicalRespawnData(level, respawnConfig.respawnData());
        if (respawnData == respawnConfig.respawnData()) {
            return respawnConfig;
        }
        return new ServerPlayer.RespawnConfig(respawnData, respawnConfig.forced());
    }

    @Nullable
    public static BlockPos findWanderingTraderSpawnPositionNear(
            LevelReader level,
            BlockPos referencePosition,
            int radius,
            RandomSource random) {
        return findEventSpawnPositionNear(level, referencePosition, radius, random, EntityType.WANDERING_TRADER);
    }

    @Nullable
    public static Vec3 findVillageSiegeSpawnPositionNear(ServerLevel level, BlockPos referencePosition, RandomSource random) {
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (!tiling.enabled()) {
            return null;
        }

        for (int i = 0; i < EVENT_SPAWN_ATTEMPTS; i++) {
            int rawX = referencePosition.getX() + random.nextInt(16) - 8;
            int rawZ = referencePosition.getZ() + random.nextInt(16) - 8;
            int x = CoordUtil.wrapBlock(tiling, rawX);
            int z = CoordUtil.wrapBlock(tiling, rawZ);
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            if (level.isVillage(candidate)
                    && net.minecraft.world.entity.monster.Monster.checkMonsterSpawnRules(
                            EntityType.ZOMBIE, level, net.minecraft.world.entity.EntitySpawnReason.EVENT, candidate, random)) {
                return Vec3.atBottomCenterOf(candidate);
            }
        }

        return null;
    }

    @Nullable
    private static BlockPos findEventSpawnPositionNear(
            LevelReader level,
            BlockPos referencePosition,
            int radius,
            RandomSource random,
            EntityType<?> entityType) {
        DimensionTiling tiling = level instanceof net.minecraft.world.level.Level levelWithDimension
                ? DimensionTiling.forLevel(levelWithDimension)
                : DimensionTiling.currentOrOverworld();
        if (!tiling.enabled()) {
            return null;
        }

        BlockPos sampleReference = visibleReference(level, tiling, referencePosition);
        SpawnPlacementType placementType = SpawnPlacements.getPlacementType(entityType);
        for (int i = 0; i < EVENT_SPAWN_ATTEMPTS; i++) {
            int rawX = sampleReference.getX() + random.nextInt(radius * 2) - radius;
            int rawZ = sampleReference.getZ() + random.nextInt(radius * 2) - radius;
            int x = CoordUtil.wrapBlock(tiling, rawX);
            int z = CoordUtil.wrapBlock(tiling, rawZ);
            int y = level.getHeight(SpawnPlacements.getHeightmapType(entityType), x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            if (placementType.isSpawnPositionOk(level, candidate, entityType)) {
                return candidate;
            }
        }

        return null;
    }

    private static BlockPos visibleReference(LevelReader level, DimensionTiling tiling, BlockPos referencePosition) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return referencePosition;
        }

        Player nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : serverLevel.players()) {
            double distance = CoordUtil.wrappedDistanceSqr(
                    tiling,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    referencePosition.getX() + 0.5,
                    referencePosition.getY() + 0.5,
                    referencePosition.getZ() + 0.5);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPlayer = player;
            }
        }

        if (nearestPlayer == null) {
            return referencePosition;
        }
        int x = (int)CoordUtil.virtualBlock(tiling, referencePosition.getX(), nearestPlayer.getX());
        int z = (int)CoordUtil.virtualBlock(tiling, referencePosition.getZ(), nearestPlayer.getZ());
        return new BlockPos(x, referencePosition.getY(), z);
    }

    @Nullable
    private static BlockPos findDryLandInChunk(ServerLevel level, ChunkPos chunkPos) {
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                BlockPos pos = getOverworldRespawnPos(level, x, z);
                if (pos != null && noCollisionNoLiquid(level, pos)) {
                    return pos;
                }
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos findSafeSurfaceInChunk(ServerLevel level, ChunkPos chunkPos) {
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (y < level.getMinY()) {
                    continue;
                }
                BlockPos pos = new BlockPos(x, y, z);
                if (noCollision(level, pos)) {
                    return pos;
                }
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos getOverworldRespawnPos(ServerLevel level, int x, int z) {
        boolean caveWorld = level.dimensionType().hasCeiling();
        LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        int topY = caveWorld
                ? level.getChunkSource().getGenerator().getSpawnHeight(level)
                : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
        if (topY < level.getMinY()) {
            return null;
        }

        int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        if (surface <= topY && surface > chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15)) {
            return null;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = topY + 1; y >= level.getMinY(); y--) {
            pos.set(x, y, z);
            BlockState blockState = level.getBlockState(pos);
            if (!blockState.getFluidState().isEmpty()) {
                break;
            }

            if (Block.isFaceFull(blockState.getCollisionShape(level, pos), Direction.UP)) {
                return pos.above().immutable();
            }
        }

        return null;
    }

    private static Vec3 fixupSpawnHeightInTile(CollisionGetter level, BlockPos spawnPos) {
        BlockPos canonicalSpawnPos = level instanceof net.minecraft.world.level.Level levelWithDimension
                ? CoordUtil.wrapBlockPos(levelWithDimension, spawnPos)
                : CoordUtil.wrapBlockPos(spawnPos);
        BlockPos.MutableBlockPos mutablePos = canonicalSpawnPos.mutable();

        while (!noCollisionNoLiquid(level, mutablePos) && mutablePos.getY() < level.getMaxY()) {
            mutablePos.move(Direction.UP);
        }

        mutablePos.move(Direction.DOWN);

        while (noCollisionNoLiquid(level, mutablePos) && mutablePos.getY() > level.getMinY()) {
            mutablePos.move(Direction.DOWN);
        }

        mutablePos.move(Direction.UP);
        return Vec3.atBottomCenterOf(mutablePos);
    }

    private static Vec3 lastResort(ServerLevel level, DimensionTiling tiling) {
        int x = CoordUtil.wrapBlock(tiling, 0);
        int z = CoordUtil.wrapBlock(tiling, 0);
        int y = level.getChunkSource().getGenerator().getSpawnHeight(level);
        if (y < level.getMinY()) {
            y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        }
        BlockPos pos = new BlockPos(x, Math.max(level.getMinY(), y), z);
        GlobeWorld.LOGGER.warn("Globe spawn search found no proven safe spawn in {}; using {}", level.dimension().identifier(), pos);
        return Vec3.atBottomCenterOf(pos);
    }

    private static boolean noCollisionNoLiquid(CollisionGetter level, BlockPos pos) {
        return level.noCollision(null, PLAYER_DIMENSIONS.makeBoundingBox(pos.getBottomCenter()), true);
    }

    private static boolean noCollision(CollisionGetter level, BlockPos pos) {
        return level.noCollision(null, PLAYER_DIMENSIONS.makeBoundingBox(pos.getBottomCenter()));
    }

    private static CanonicalChunkSearch orderedCanonicalChunks(DimensionTiling tiling, BlockPos suggestion) {
        return new CanonicalChunkSearch(tiling, suggestion);
    }

    private static final class SpawnSearch {
        private final ServerLevel level;
        private final DimensionTiling tiling;
        private final BlockPos suggestion;
        private final Iterator<ChunkPos> chunks;
        private final CompletableFuture<Vec3> future = new CompletableFuture<>();

        private SpawnSearch(ServerLevel level, DimensionTiling tiling, BlockPos suggestion) {
            this.level = level;
            this.tiling = tiling;
            this.suggestion = suggestion;
            this.chunks = orderedCanonicalChunks(tiling, suggestion).iterator();
        }

        private void scheduleNext() {
            if (this.future.isDone()) {
                return;
            }

            if (!this.chunks.hasNext()) {
                this.completeFallback();
                return;
            }

            ChunkPos chunk = this.chunks.next();
            this.level.getChunkSource().addTicketAndLoadWithRadius(TicketType.SPAWN_SEARCH, chunk, 0).whenCompleteAsync((ignored, throwable) -> {
                if (throwable != null) {
                    this.future.completeExceptionally(throwable);
                    return;
                }

                BlockPos pos = findDryLandInChunk(this.level, chunk);
                if (pos != null) {
                    this.future.complete(Vec3.atBottomCenterOf(pos));
                } else {
                    this.scheduleNext();
                }
            }, this.level.getServer());
        }

        private void completeFallback() {
            for (ChunkPos chunk : orderedCanonicalChunks(this.tiling, this.suggestion)) {
                BlockPos pos = findSafeSurfaceInChunk(this.level, chunk);
                if (pos != null) {
                    this.future.complete(Vec3.atBottomCenterOf(pos));
                    return;
                }
            }

            Vec3 fixedSuggestion = fixupSpawnHeightInTile(this.level, this.suggestion);
            BlockPos fixedBlock = BlockPos.containing(fixedSuggestion);
            if (CoordUtil.isInCanonicalTile(this.tiling, fixedBlock) && noCollision(this.level, fixedBlock)) {
                this.future.complete(fixedSuggestion);
                return;
            }

            this.future.complete(lastResort(this.level, this.tiling));
        }
    }

    private static final class CanonicalChunkSearch implements Iterable<ChunkPos> {
        private final DimensionTiling tiling;
        private final int tileSize;
        private final int halfTileSize;
        private final long totalChunks;
        private final ChunkPos suggestionChunk;

        private CanonicalChunkSearch(DimensionTiling tiling, BlockPos suggestion) {
            this.tiling = tiling;
            this.tileSize = tiling.tileSizeChunks();
            this.halfTileSize = this.tileSize / 2;
            this.totalChunks = (long)this.tileSize * (long)this.tileSize;
            this.suggestionChunk = CoordUtil.wrapChunkPos(
                    tiling,
                    new ChunkPos(
                            SectionPos.blockToSectionCoord(suggestion.getX()),
                            SectionPos.blockToSectionCoord(suggestion.getZ())));
        }

        @Override
        public Iterator<ChunkPos> iterator() {
            return new Iterator<>() {
                private long emitted;
                private int distance;
                private int xDistance;
                private int[] xs = new int[0];
                private int[] zs = new int[0];
                private int candidateIndex;
                private boolean hasCandidateSet;

                @Override
                public boolean hasNext() {
                    return this.emitted < CanonicalChunkSearch.this.totalChunks;
                }

                @Override
                public ChunkPos next() {
                    if (!this.hasNext()) {
                        throw new NoSuchElementException();
                    }

                    while (!this.hasCandidateSet || this.candidateIndex >= this.xs.length * this.zs.length) {
                        this.prepareNextCandidateSet();
                    }

                    int zCount = this.zs.length;
                    int x = this.xs[this.candidateIndex / zCount];
                    int z = this.zs[this.candidateIndex % zCount];
                    this.candidateIndex++;
                    this.emitted++;
                    return new ChunkPos(x, z);
                }

                private void prepareNextCandidateSet() {
                    while (true) {
                        int maxXDistance = Math.min(this.distance, CanonicalChunkSearch.this.halfTileSize);
                        if (this.xDistance > maxXDistance) {
                            this.distance++;
                            this.xDistance = 0;
                            continue;
                        }

                        int zDistance = this.distance - this.xDistance;
                        if (zDistance <= CanonicalChunkSearch.this.halfTileSize) {
                            this.xs = coordinatesAtDistance(
                                    CanonicalChunkSearch.this.suggestionChunk.x(),
                                    this.xDistance);
                            this.zs = coordinatesAtDistance(
                                    CanonicalChunkSearch.this.suggestionChunk.z(),
                                    zDistance);
                            this.candidateIndex = 0;
                            this.hasCandidateSet = true;
                            this.xDistance++;
                            return;
                        }

                        this.xDistance++;
                    }
                }
            };
        }

        private int[] coordinatesAtDistance(int origin, int distance) {
            if (distance == 0) {
                return new int[] { origin };
            }

            int negative = CoordUtil.wrapChunk(this.tiling, origin - distance);
            int positive = CoordUtil.wrapChunk(this.tiling, origin + distance);
            if (negative == positive) {
                return new int[] { negative };
            }
            return negative < positive ? new int[] { negative, positive } : new int[] { positive, negative };
        }
    }
}
