# Chunk Loading

Chunk loading is controlled by tickets, distance levels, chunk holders, and status futures. The server maintains logical chunk state and separately decides which full chunks are ticking, entity-ticking, visible, or ready to send to players.

## Key Source Files

Common sources jar:

- `net/minecraft/server/level/ServerChunkCache.java`
- `net/minecraft/server/level/ChunkMap.java`
- `net/minecraft/server/level/ChunkHolder.java`
- `net/minecraft/server/level/DistanceManager.java`
- `net/minecraft/world/level/chunk/status/ChunkStatus.java`
- `net/minecraft/world/level/chunk/ChunkAccess.java`
- `net/minecraft/world/level/chunk/LevelChunk.java`

Client-only sources jar:

- `net/minecraft/client/multiplayer/ClientChunkCache.java`

## Server Flow

`ServerChunkCache` is the main server-side entry point.

- `ServerChunkCache.getChunk(...)` looks up or requests a chunk by `(x, z, status, loadOrGenerate)`.
- `ServerChunkCache.getChunkFutureMainThread(...)` uses `ChunkHolder` futures and ticket promotion.
- The constructor creates `ChunkMap`, gets its `DistanceManager`, and forwards the configured simulation distance to `DistanceManager.updateSimulationDistance(...)`.
- `ServerChunkCache.tick(...)` runs distance manager updates, ticking chunks, and `ChunkMap.tick(...)`.
- `ServerChunkCache.tickChunks(...)` separates spawning chunks from block-ticking chunks.
- `ServerChunkCache.blockChanged(...)` marks a visible chunk holder for block update broadcast.
- `ServerChunkCache.onLightUpdate(...)` marks a visible chunk holder for light update broadcast.
- `ServerChunkCache.setSimulationDistance(...)` forwards runtime simulation-distance changes to `DistanceManager.updateSimulationDistance(...)`.

Important anchors:

- `ServerChunkCache.java:148` `getChunk`
- `ServerChunkCache.java:232` `getChunkFutureMainThread`
- `ServerChunkCache.java:285` `runDistanceManagerUpdates`
- `ServerChunkCache.java:320` `tick`
- `ServerChunkCache.java:340` `tickChunks`
- `ServerChunkCache.java:465` `blockChanged`
- `ServerChunkCache.java:475` `onLightUpdate`
- `ServerChunkCache.java:121` constructor call to `DistanceManager.updateSimulationDistance(...)`
- `ServerChunkCache.java:553` `setSimulationDistance`

Simulation distance is stored globally in `PlayerList`, but applied per level
through each `ServerChunkCache`.

- `PlayerList.placeNewPlayer(...)` sends `getSimulationDistance()` in the login packet.
- `PlayerList.setSimulationDistance(...)` stores the configured value, broadcasts `ClientboundSetSimulationDistancePacket`, then calls `level.getChunkSource().setSimulationDistance(...)` for every level.
- `DistanceManager.updateSimulationDistance(...)` stores the value and replaces `PLAYER_SIMULATION` ticket levels using `ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) - simulationDistance`.
- `SimulationChunkTracker` inherits `ChunkTracker`, whose distance propagation checks all eight neighboring chunks and adds one level per step. Simulation radius therefore behaves like Chebyshev chunk distance for square regions, so diagonal corners are not farther than edges for this tracker.

Important anchors:

- `ChunkTracker.java:18` `checkNeighborsAfterUpdate`
- `ChunkTracker.java:36` `getComputedLevel`
- `ChunkTracker.java:58` `computeLevelFromNeighbor`
- `PlayerList.java:169` login packet construction
- `PlayerList.java:175` login simulation-distance value
- `PlayerList.java:691` `getSimulationDistance`
- `PlayerList.java:808` `setSimulationDistance`
- `PlayerList.java:810` `ClientboundSetSimulationDistancePacket`
- `PlayerList.java:813` per-level `ServerChunkCache.setSimulationDistance(...)`
- `DistanceManager.java:49` `simulationDistance`
- `DistanceManager.java:119` adds `PLAYER_SIMULATION` tickets
- `DistanceManager.java:135` `getPlayerTicketLevel`
- `DistanceManager.java:155` `updateSimulationDistance`

`ChunkMap` owns the live chunk-holder maps and player-facing tracking.

- `visibleChunkMap` is the promoted view used by readers.
- `updatingChunkMap` is the mutable scheduling view.
- `updateChunkScheduling(...)` creates or updates `ChunkHolder` instances.
- `prepareTickingChunk(...)`, `prepareEntityTickingChunk(...)`, and `prepareAccessibleChunk(...)` define readiness levels.
- `applyChunkTrackingView(...)` sends chunk cache center updates and schedules chunk send/drop operations for players.

Important anchors:

- `ChunkMap.java:127` `updatingChunkMap`
- `ChunkMap.java:128` `visibleChunkMap`
- `ChunkMap.java:368` `updateChunkScheduling`
- `ChunkMap.java:671` `prepareTickingChunk`
- `ChunkMap.java:362` `prepareEntityTickingChunk`
- `ChunkMap.java:701` `prepareAccessibleChunk`
- `ChunkMap.java:1102` `applyChunkTrackingView`

## Status Pipeline

`ChunkStatus` names the generation/load progression:

`EMPTY -> STRUCTURE_STARTS -> STRUCTURE_REFERENCES -> BIOMES -> NOISE -> SURFACE -> CARVERS -> FEATURES -> INITIALIZE_LIGHT -> LIGHT -> SPAWN -> FULL`

Anchor:

- `net/minecraft/world/level/chunk/status/ChunkStatus.java:21`

## Client Flow

The client stores chunks in `ClientChunkCache.Storage`, indexed around a moving view center.

- `ClientChunkCache.replaceWithPacketData(...)` creates or updates a `LevelChunk` from a chunk packet.
- `ClientChunkCache.drop(...)` removes a chunk by `ChunkPos`.
- `ClientChunkCache.updateViewCenter(...)` updates the client-side center.
- `ClientChunkCache.Storage.getIndex(...)` uses floor-mod indexing over the local storage array.

Important anchors:

- `ClientChunkCache.java:59` `drop`
- `ClientChunkCache.java:100` `replaceWithPacketData`
- `ClientChunkCache.java:127` `updateViewCenter`
- `ClientChunkCache.java:196` `Storage.getIndex`
- `ClientChunkCache.java:270` `Storage.inRange`

## Audit Questions

- Which code path treats `ChunkPos` as storage identity rather than a view coordinate?
- Which code path treats `ChunkPos` as player-facing packet position?
- Do ticket keys, visible chunk keys, and generated chunk keys need the same coordinate space?
- Are chunk send/drop decisions based on player distance, chunk tracking view, or explicit packet state?
- Does a chunk operation require `FULL`, `LIGHT`, or an earlier generation status?

## Globe World Notes

The current project model splits chunk behavior into two identities:

- Canonical chunks own mutable world state and should be the only chunks that run simulation effects.
- Alias chunks are player-facing view coordinates. They may become visible, block-ticking, or entity-ticking in vanilla distance management, while canonical chunks are kept loaded with loading-only alias tickets.

Project hooks:

- `src/main/java/globe/world/mixin/ServerChunkCacheMixin.java:28` caps the constructor simulation-distance argument before it reaches `DistanceManager`.
- `src/main/java/globe/world/mixin/ServerChunkCacheMixin.java:39` caps the runtime `setSimulationDistance(...)` argument before it reaches `DistanceManager`.
- `src/main/java/globe/world/mixin/ServerChunkCacheMixin.java:50` wraps `ServerChunkCache.getChunk(...)` to canonical chunk coordinates.
- `src/main/java/globe/world/mixin/ServerChunkCacheMixin.java:60` wraps `ServerChunkCache.getChunkNow(...)`.
- `src/main/java/globe/world/mixin/ServerChunkCacheMixin.java:69` wraps `blockChanged(...)` to canonical block coordinates before vanilla chunk-holder broadcast bookkeeping.
- `src/main/java/globe/world/mixin/ServerChunkCacheMixin.java:78` clears Globe alias tickets during vanilla shutdown ticket deactivation, with `close()` as a backup.
- `src/main/java/globe/world/mixin/ChunkMapCanonicalTicketMixin.java:19` observes `ChunkMap.onFullChunkStatusChange(...)` and tracks non-canonical aliases.
- `src/main/java/globe/world/util/CanonicalChunkTickets.java:25` records alias full-chunk status.
- `src/main/java/globe/world/util/CanonicalChunkTickets.java:121` and `:140` ref-count canonical alias tickets, so multiple players or multiple aliases can keep the same canonical chunk loaded without prematurely unloading it.
- `src/main/java/globe/world/util/GlobeDistanceCaps.java` owns the pure effective render and simulation distance policy.

Current status:

- Good: canonical chunks stay loaded when players are near non-canonical aliases.
- Good: the ref-count keys include `ServerLevel`, canonical chunk, and radius, so multiplayer aliases share canonical tickets safely.
- Good: canonical alias tickets are loading-only, avoiding mid-tick mutation of vanilla simulation-distance tracker state.
- Important caveat: ticket mirroring only guarantees canonical availability. Each vanilla tick lane still needs its own decision about whether alias chunks are allowed to run behavior or must be converted/deduped to canonical chunks.

Best rule of thumb:

Simulation code should run once per canonical chunk per relevant server tick lane. View and packet code may operate on aliases, then relabel or fan out as needed.
