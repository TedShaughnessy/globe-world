package globe.world.util;

import globe.world.diagnostics.DiagnosticsChannel;
import globe.world.diagnostics.GlobeDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WorldGenSpillover {
    private static final long STALE_WARNING_TICKS = 1200L;

    private WorldGenSpillover() {
    }

    public static void enqueue(ServerLevel level, BlockPos pos, BlockState expectedState, BlockState blockState, int flags) {
        if (!DimensionTiling.forLevel(level).enabled()) {
            return;
        }

        spilloverState(level).enqueue(level, pos, expectedState, blockState, flags);
    }

    public static void applyToChunk(ServerLevel level, ChunkAccess chunk) {
        if (!DimensionTiling.forLevel(level).enabled()) {
            return;
        }

        spilloverState(level).applyToChunk(level, chunk);
    }

    public static int clearLevel(ServerLevel level) {
        return spilloverState(level).clearLevel(level);
    }

    public static int clearAll(MinecraftServer server) {
        return spilloverState(server).clearAll();
    }

    public static int pendingWriteCount(MinecraftServer server) {
        return spilloverState(server).pendingWriteCount();
    }

    public static int pendingWriteCount(ServerLevel level) {
        return spilloverState(level).pendingWriteCount(level.dimension());
    }

    public static int pendingChunkCount(ServerLevel level) {
        return spilloverState(level).pendingChunkCount(level.dimension());
    }

    private static State spilloverState(ServerLevel level) {
        return spilloverState(level.getServer());
    }

    private static State spilloverState(MinecraftServer server) {
        return ((WorldGenSpilloverOwner) server).globeWorld$spilloverState();
    }

    public static final class State {
        private final Map<Key, Queue> pendingWrites = new HashMap<>();

        public synchronized void enqueue(ServerLevel level, BlockPos pos, BlockState expectedState, BlockState state, int flags) {
            BlockPos wrapped = CoordUtil.wrapBlockPos(level, pos).immutable();
            ChunkPos chunkPos = new ChunkPos(
                    SectionPos.blockToSectionCoord(wrapped.getX()),
                    SectionPos.blockToSectionCoord(wrapped.getZ())
            );
            Key key = new Key(level.dimension(), chunkPos.pack());
            Queue queue = this.pendingWrites.computeIfAbsent(key, ignored -> new Queue(level.getGameTime()));
            queue.add(new Write(wrapped, expectedState, state, flags), level.getGameTime());
            GlobeDiagnostics.debug(
                    DiagnosticsChannel.WORLDGEN,
                    "GW_WORLDGEN_SPILLOVER enqueue dimension={} canonical={} pos={} guarded={} expected={} queued={}",
                    dimensionName(key.dimension()),
                    format(chunkPos),
                    wrapped.toShortString(),
                    expectedState != null,
                    expectedState,
                    state
            );
            warnIfStale(level, key, queue);
        }

        public synchronized void applyToChunk(ServerLevel level, ChunkAccess chunk) {
            ChunkPos chunkPos = chunk.getPos();
            if (CoordUtil.wrapChunk(level, chunkPos.x()) != chunkPos.x()
                    || CoordUtil.wrapChunk(level, chunkPos.z()) != chunkPos.z()) {
                return;
            }

            Key key = new Key(level.dimension(), chunkPos.pack());
            Queue queue = this.pendingWrites.remove(key);
            if (queue == null) {
                return;
            }

            warnIfStale(level, key, queue);
            GlobeDiagnostics.debug(
                    DiagnosticsChannel.WORLDGEN,
                    "GW_WORLDGEN_SPILLOVER apply dimension={} canonical={} writes={} ageTicks={}",
                    dimensionName(key.dimension()),
                    format(chunkPos),
                    queue.writeCount(),
                    ageTicks(level, queue)
            );

            for (Write write : queue.writes()) {
                BlockState currentState = chunk.getBlockState(write.pos());
                if (write.expectedState() == null || currentState.equals(write.expectedState())) {
                    chunk.setBlockState(write.pos(), write.state(), write.flags());
                } else {
                    GlobeDiagnostics.debug(
                            DiagnosticsChannel.WORLDGEN,
                            "GW_WORLDGEN_SPILLOVER skip dimension={} canonical={} pos={} expected={} actual={} queued={}",
                            dimensionName(key.dimension()),
                            format(chunkPos),
                            write.pos().toShortString(),
                            write.expectedState(),
                            currentState,
                            write.state()
                    );
                }
            }
        }

        public synchronized int clearLevel(ServerLevel level) {
            ResourceKey<Level> dimension = level.dimension();
            int discardedWrites = 0;
            int discardedChunks = 0;
            for (var iterator = this.pendingWrites.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry<Key, Queue> entry = iterator.next();
                if (!entry.getKey().dimension().equals(dimension)) {
                    continue;
                }

                discardedWrites += entry.getValue().writeCount();
                discardedChunks++;
                iterator.remove();
            }
            logCleanup("level", discardedWrites, discardedChunks, dimension);
            return discardedWrites;
        }

        public synchronized int clearAll() {
            int discardedWrites = pendingWriteCount();
            int discardedChunks = this.pendingWrites.size();
            this.pendingWrites.clear();
            logCleanup("all", discardedWrites, discardedChunks, null);
            return discardedWrites;
        }

        public synchronized int pendingWriteCount() {
            int total = 0;
            for (Queue queue : this.pendingWrites.values()) {
                total += queue.writeCount();
            }
            return total;
        }

        public synchronized int pendingWriteCount(ResourceKey<Level> dimension) {
            int total = 0;
            for (Map.Entry<Key, Queue> entry : this.pendingWrites.entrySet()) {
                if (entry.getKey().dimension().equals(dimension)) {
                    total += entry.getValue().writeCount();
                }
            }
            return total;
        }

        public synchronized int pendingChunkCount(ResourceKey<Level> dimension) {
            int total = 0;
            for (Key key : this.pendingWrites.keySet()) {
                if (key.dimension().equals(dimension)) {
                    total++;
                }
            }
            return total;
        }

        private static void warnIfStale(ServerLevel level, Key key, Queue queue) {
            long gameTime = level.getGameTime();
            long ageTicks = gameTime - queue.firstQueuedTick();
            if (ageTicks < STALE_WARNING_TICKS || gameTime - queue.lastWarningTick() < STALE_WARNING_TICKS) {
                return;
            }

            queue.markWarned(gameTime);
            GlobeDiagnostics.warn(
                    DiagnosticsChannel.WORLDGEN,
                    "GW_WORLDGEN_SPILLOVER stale dimension={} canonical={} writes={} ageTicks={}",
                    dimensionName(key.dimension()),
                    format(ChunkPos.unpack(key.canonicalChunk())),
                    queue.writeCount(),
                    ageTicks
            );
        }

        private static void logCleanup(
                String scope,
                int discardedWrites,
                int discardedChunks,
                ResourceKey<Level> dimension) {
            if (discardedWrites <= 0) {
                return;
            }

            GlobeDiagnostics.warn(
                    DiagnosticsChannel.WORLDGEN,
                    "GW_WORLDGEN_SPILLOVER cleanup scope={} discardedWrites={} discardedChunks={} dimension={}",
                    scope,
                    discardedWrites,
                    discardedChunks,
                    dimension == null ? "*" : dimensionName(dimension)
            );
        }

        private static long ageTicks(ServerLevel level, Queue queue) {
            return level.getGameTime() - queue.firstQueuedTick();
        }

        private static String format(ChunkPos pos) {
            return pos.x() + "," + pos.z();
        }

        private static String dimensionName(ResourceKey<Level> dimension) {
            return dimension.identifier().toString();
        }
    }

    private static final class Queue {
        private final List<Write> writes = new ArrayList<>();
        private final long firstQueuedTick;
        private long lastQueuedTick;
        private long lastWarningTick = -STALE_WARNING_TICKS;

        private Queue(long firstQueuedTick) {
            this.firstQueuedTick = firstQueuedTick;
            this.lastQueuedTick = firstQueuedTick;
        }

        private void add(Write write, long gameTime) {
            this.writes.add(write);
            this.lastQueuedTick = gameTime;
        }

        private List<Write> writes() {
            return this.writes;
        }

        private int writeCount() {
            return this.writes.size();
        }

        private long firstQueuedTick() {
            return this.firstQueuedTick;
        }

        private long lastWarningTick() {
            return this.lastWarningTick;
        }

        private void markWarned(long gameTime) {
            this.lastWarningTick = gameTime;
        }
    }

    private record Key(ResourceKey<Level> dimension, long canonicalChunk) {
    }

    private record Write(BlockPos pos, BlockState expectedState, BlockState state, int flags) {
    }
}
