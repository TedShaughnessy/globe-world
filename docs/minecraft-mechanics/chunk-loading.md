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
- `ServerChunkCache.tick(...)` runs distance manager updates, ticking chunks, and `ChunkMap.tick(...)`.
- `ServerChunkCache.tickChunks(...)` separates spawning chunks from block-ticking chunks.
- `ServerChunkCache.blockChanged(...)` marks a visible chunk holder for block update broadcast.
- `ServerChunkCache.onLightUpdate(...)` marks a visible chunk holder for light update broadcast.

Important anchors:

- `ServerChunkCache.java:148` `getChunk`
- `ServerChunkCache.java:232` `getChunkFutureMainThread`
- `ServerChunkCache.java:285` `runDistanceManagerUpdates`
- `ServerChunkCache.java:320` `tick`
- `ServerChunkCache.java:340` `tickChunks`
- `ServerChunkCache.java:465` `blockChanged`
- `ServerChunkCache.java:475` `onLightUpdate`

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

