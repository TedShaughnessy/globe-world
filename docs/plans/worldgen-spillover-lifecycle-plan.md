# Worldgen Spillover Lifecycle Plan

Status: core lifecycle implementation completed. Durable behavior is documented
in [../mod-mechanics/worldgen.md](../mod-mechanics/worldgen.md). Keep this plan
until manual validation confirms whether another canonical-load apply hook is
needed.

## Problem

Before the lifecycle fix, `WorldGenSpillover` stored delayed wrapped worldgen
writes in a static map whose key contained a raw `ServerLevel`. A pending write
could retain an unloaded level if generation aborted, a world closed, or the
target canonical chunk never reached one of the later apply hooks.

That creates three risks:

- retained `ServerLevel` references after unload
- silent discarded-or-never-applied worldgen writes
- stale writes from an old generation lifecycle being applied at the wrong time

## Current Hooks

- `WorldGenRegionMixin.setBlock(...)` canonicalizes wrapped worldgen block
  writes and calls `WorldGenSpillover.enqueue(...)`.
- `ChunkGeneratorMixin.applyBiomeDecoration(...)` applies queued writes before
  and after feature/structure decoration for canonical chunks, and skips alias
  biome decoration.
- `LevelChunkPostProcessMixin.postProcessGeneration(...)` applies queued writes
  for canonical chunks and cancels alias post-processing.
- `PlayerChunkSenderMixin.sendChunk(...)` applies queued writes before building
  a chunk packet.
- `ServerChunkCacheMixin` clears spillover queues next to
  `CanonicalChunkTickets` and `ChunkAliasTracker` level cleanup.
- `MinecraftServerMixin.stopServer(...)` clears the active server's spillover
  state next to `ChunkAliasTracker`.

## Goals

- Pending spillover writes must not retain old `ServerLevel` instances.
- Pending spillover writes from one server lifecycle must not be visible to a
  later integrated-server/world lifecycle.
- Lifecycle cleanup must make non-empty discarded queues visible in logs.
- Canonical chunks remain the only intended persisted worldgen targets.
- Edge feature and structure spillover must still land in canonical chunks.
- The implementation should stay close to the existing mixin style and avoid
  adding new lifecycle hooks when existing hooks cover the boundary.

## Implemented Core

### 1. Move Pending State Into The Server Lifecycle

Keep `WorldGenSpillover` as a static facade, but move the pending-write map into
a state object owned by the active `MinecraftServer`. This makes the server
lifecycle the outer boundary for spillover data: if an integrated server closes
and a later one opens with the same dimension keys, it receives a fresh
spillover state instead of seeing old queues.

Add a small accessor interface:

- `WorldGenSpilloverOwner`
- method: `WorldGenSpillover.State globeWorld$spilloverState()`

Extend `MinecraftServerMixin`:

- add a final `WorldGenSpillover.State` field
- implement `WorldGenSpilloverOwner`
- return the field from `globeWorld$spilloverState()`

Inside `WorldGenSpillover`, resolve state from the level:

- `((WorldGenSpilloverOwner) level.getServer()).globeWorld$spilloverState()`

The facade methods stay static so existing call sites remain simple:

- `enqueue(ServerLevel level, BlockPos pos, BlockState state, int flags)`
- `applyToChunk(ServerLevel level, ChunkAccess chunk)`
- `int clearLevel(ServerLevel level)`
- `int clearAll(MinecraftServer server)`
- `int pendingWriteCount(MinecraftServer server)`
- `int pendingWriteCount(ServerLevel level)`
- `int pendingChunkCount(ServerLevel level)`

`clearAll(...)` and whole-server counters should take a `MinecraftServer`
argument because there is no longer one global spillover map to clear.

### 2. Make The Inner Keys Lifecycle-Safe

Inside `WorldGenSpillover.State`, replace the current key shape:

- from `Key(ServerLevel level, long chunkPos)`
- to `Key(ResourceKey<Level> dimension, long canonicalChunk)`

Keep `ServerLevel` as a method argument for wrapping, game-time diagnostics, and
application, but do not store it in the state object's pending map.

Implementation notes:

- `enqueue(...)` should continue to wrap `pos` with
  `CoordUtil.wrapBlockPos(level, pos)` and store only the immutable wrapped
  `BlockPos`, `BlockState`, and flags.
- `applyToChunk(...)` should still no-op for disabled tiling and non-canonical
  chunks.
- Use `level.dimension()` plus `chunk.getPos().pack()` for lookups.
- Keep methods synchronized unless the implementation is intentionally moved to
  a concurrent map; worldgen can run off-thread, and the current synchronized
  API is the simplest safe continuation.

### 3. Store Per-Chunk Queue Metadata

Replace each pending value from a bare `List<Write>` with a small queue record:

- `List<Write> writes`
- `long firstQueuedTick`
- `long lastQueuedTick`
- `long lastWarningTick`

Use `level.getGameTime()` when enqueueing. The tick value is mainly diagnostic;
server startup or heavy generation may not advance it normally, so cleanup logs
remain the authoritative signal for abandoned writes.

When `applyToChunk(...)` removes a queue, apply all writes to the supplied
canonical chunk in FIFO order. If debug logging is enabled, emit one compact log
when a non-empty queue is applied:

`GW_WORLDGEN_SPILLOVER apply dimension={} canonical={} writes={} ageTicks={}`

### 4. Add Explicit Cleanup

Add cleanup methods to `WorldGenSpillover`:

- `clearLevel(ServerLevel level)` removes every queue whose key dimension
  matches `level.dimension()` from `level.getServer()`'s spillover state.
- `clearAll(MinecraftServer server)` removes every queue from that server's
  spillover state.
- Both return the number of discarded writes, not just the number of chunk
  queues, so shutdown output reflects data loss risk.
- If a cleanup discards any writes, log at warn level:

`GW_WORLDGEN_SPILLOVER cleanup scope={} discardedWrites={} discardedChunks={} dimension={}`

Wire cleanup into existing lifecycle hooks:

- In `ServerChunkCacheMixin.deactivateTicketsOnClosing(...)` `HEAD`, call
  `WorldGenSpillover.clearLevel(this.level)` next to
  `CanonicalChunkTickets.clearLevel(this.level)` and
  `ChunkAliasTracker.clearLevel(this.level.dimension())`.
- In `ServerChunkCacheMixin.close(...)` `HEAD`, call the same cleanup again.
  Cleanup must be idempotent; the second call should normally remove zero.
- In `MinecraftServerMixin.stopServer(...)` `HEAD`, call
  `WorldGenSpillover.clearAll((MinecraftServer) (Object) this)` next to
  `ChunkAliasTracker.clearAll()`.

Do not remove or weaken existing apply hooks. Server-scoped state is the primary
cross-lifecycle containment; cleanup is the normal shutdown hygiene and the
place where abandoned writes become visible.

### 5. Add Stale Queue Diagnostics

Add low-noise diagnostics inside `WorldGenSpillover`:

- Count pending writes by dimension and canonical chunk.
- On enqueue and apply, call a private `warnIfStale(level, key, queue)` helper.
- Treat a queue older than `1200` game ticks as stale.
- Rate-limit each queue's stale warning to once every `1200` game ticks.
- Use warn level because a stale queue means canonical worldgen writes have not
  reached an apply point:

`GW_WORLDGEN_SPILLOVER stale dimension={} canonical={} writes={} ageTicks={}`

Avoid per-write logs. The useful diagnostic unit is the canonical chunk queue.

### 6. Preserve And Audit Application Ordering

Keep the current apply sites, but validate them in this order:

1. `ChunkGeneratorMixin.applyBiomeDecoration(...)` `HEAD` applies spillover that
   was queued before the canonical chunk decorates.
2. `ChunkGeneratorMixin.applyBiomeDecoration(...)` `RETURN` applies spillover
   queued during that chunk's own decoration.
3. `LevelChunkPostProcessMixin.postProcessGeneration(...)` applies spillover
   before canonical neighbor-shape/post-processing work runs.
4. `PlayerChunkSenderMixin.sendChunk(...)` applies spillover before packet
   serialization, covering canonical chunks loaded or sent after another edge
   feature queued writes for them.

After adding diagnostics, stress generation near tile edges. If stale warnings
show queues for loaded canonical chunks that are not sent to players, add a
focused canonical-load apply hook rather than broadening every chunk lookup:

- preferred candidate: a `ChunkStatusTasks.generateFeatures(...)` `RETURN` hook
  that calls `WorldGenSpillover.applyToChunk(context.level(), chunk)` for
  canonical chunks, because vanilla creates the `WorldGenRegion` and runs biome
  decoration in that task.
- fallback candidate: a level-chunk promotion/load hook, only if it can be
  targeted to canonical chunks and avoid applying writes during arbitrary
  read-only chunk access.

### 7. Document Implemented Behavior

`docs/mod-mechanics/worldgen.md` now records the durable behavior:

- Spillover is a delayed canonical write queue for worldgen writes that wrap
  across X/Z tile edges.
- Spillover state is owned by the active `MinecraftServer`, so queues cannot be
  found by a later server lifecycle.
- Inner queue keys are dimension and canonical chunk, not raw `ServerLevel`.
- Queues are applied during canonical biome decoration, canonical
  post-processing, and before chunk packet serialization.
- Level close and server stop discard leftover queues and warn if any writes
  were abandoned.
- Spillover is transient runtime bookkeeping and is not saved world data.

Keep this plan only until stale-write investigation confirms whether an
additional apply hook is needed.

## Completed Implementation

1. Added `WorldGenSpilloverOwner` and a `WorldGenSpillover.State` field on
   `MinecraftServerMixin`.
2. Moved pending queues into `WorldGenSpillover.State`; they now use
   dimension/canonical-chunk keys inside that state.
3. Added cleanup/count APIs on the static facade that delegate to the
   server-owned state.
4. Added apply, cleanup, and stale-warning logs.
5. Wired `WorldGenSpillover.clearLevel(...)` into `ServerChunkCacheMixin`.
6. Wired `WorldGenSpillover.clearAll(server)` into `MinecraftServerMixin`.
7. Updated `docs/mod-mechanics/worldgen.md` with the durable behavior.

## Remaining Work

1. Compile, then run the manual validation scenarios.
2. If stale warnings remain in normal generation, add the targeted
   `ChunkStatusTasks.generateFeatures(...)` return hook and document why it is
   needed.

## Vanilla Source Anchors Checked

- `ServerChunkCache.close()` closes saved data, light engine, and chunk map.
- `ServerChunkCache.deactivateTicketsOnClosing()` deactivates ticket storage
  during shutdown.
- `MinecraftServer.stopServer()` removes players, drains chunk work, saves
  chunks, closes levels, and then closes server resources.
- `ChunkStatusTasks.generateFeatures(...)` creates the `WorldGenRegion`, calls
  `ChunkGenerator.applyBiomeDecoration(...)`, then generates border ticks.
- `WorldGenRegion.setBlock(...)` writes through the region chunk after
  `ensureCanWrite(...)`.
- `LevelChunk.postProcessGeneration(...)` runs canonical post-processing for a
  promoted chunk.
- `PlayerChunkSender.sendChunk(...)` serializes
  `ClientboundLevelChunkWithLightPacket`.

## Validation

- Generate trees, structures, and other features across each tile edge and
  confirm writes land in canonical chunks.
- Use a tiny tile with high feature density and watch for stale warnings during
  normal generation; expected result is no recurring stale queues.
- Stop the world during active generation and confirm cleanup logs discard any
  remaining writes without retaining `ServerLevel`.
- Unload/reopen an integrated world and confirm the new server has a fresh
  spillover state with zero pending counts.
- Send an alias chunk near an edge and confirm the packet path still applies
  pending writes before serialization.
- Confirm cleanup methods are idempotent by checking that `close` after
  `deactivateTicketsOnClosing` removes zero additional writes in the normal
  case.
