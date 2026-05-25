# Block Updates

Block updates start with a block state mutation, then fan out into dirty marking, client packet notification, shape updates, neighbor updates, block entity side effects, and scheduled ticks.

## Key Source Files

Common sources jar:

- `net/minecraft/world/level/Level.java`
- `net/minecraft/server/level/ServerLevel.java`
- `net/minecraft/world/level/chunk/LevelChunk.java`
- `net/minecraft/world/level/block/Block.java`
- `net/minecraft/world/level/redstone/NeighborUpdater.java`
- `net/minecraft/world/level/redstone/CollectingNeighborUpdater.java`
- `net/minecraft/network/protocol/game/ClientboundBlockUpdatePacket.java`
- `net/minecraft/network/protocol/game/ClientboundSectionBlocksUpdatePacket.java`

Client-only sources jar:

- `net/minecraft/client/multiplayer/ClientPacketListener.java`

## Mutation Flow

`Level.setBlock(...)` is the central mutation path.

- It resolves the containing chunk with `getChunkAt(pos)`.
- It calls `LevelChunk.setBlockState(...)`.
- It calls `setBlocksDirty(...)`.
- If client updates are requested, it calls `sendBlockUpdated(...)`.
- If neighbor updates are requested, it calls `updateNeighborsAt(...)`.

Important anchors:

- `Level.java:217` `setBlock(pos, state, flags)`
- `Level.java:222` `setBlock(pos, state, flags, updateLimit)`
- `Level.java:228` `getChunkAt(pos)`
- `Level.java:230` `chunk.setBlockState(...)`
- `Level.java:237` `setBlocksDirty(...)`
- `Level.java:243` `sendBlockUpdated(...)`
- `Level.java:247` `updateNeighborsAt(...)`

`ServerLevel.sendBlockUpdated(...)` bridges block mutation to chunk broadcast:

- `ServerLevel.java:1122` `sendBlockUpdated`
- `ServerLevel.java:1128` `getChunkSource().blockChanged(pos)`

`ServerChunkCache.blockChanged(...)` finds the visible `ChunkHolder` for `ChunkPos.pack(x, z)` and queues the holder for broadcast:

- `ServerChunkCache.java:465` `blockChanged`

## Neighbor Updates

Neighbor updates are mediated by `Level.neighborUpdater`, which is a `CollectingNeighborUpdater`.

Important anchors:

- `Level.java:112` `neighborUpdater`
- `Level.java:154` `new CollectingNeighborUpdater(...)`
- `Level.java:314` `updateNeighborsAt`
- `Level.java:322` `neighborChanged`
- `Level.java:331` `neighborShapeChanged`
- `ServerLevel.java:1161` `updateNeighborsAt`
- `ServerLevel.java:1172` `neighborChanged`

`NeighborUpdater` defines the update order and low-level execution helpers:

- `NeighborUpdater.java:18` interface
- `NeighborUpdater.java:19` `UPDATE_ORDER`
- `NeighborUpdater.java:27` `updateNeighborsAtExceptFromFacing`
- `NeighborUpdater.java:37` `executeShapeUpdate`
- `NeighborUpdater.java:62` `executeUpdate`

`CollectingNeighborUpdater` batches cascading updates and enforces a chained-update limit:

- `CollectingNeighborUpdater.java:37` `shapeUpdate`
- `CollectingNeighborUpdater.java:49` simple `neighborChanged`
- `CollectingNeighborUpdater.java:61` `updateNeighborsAtExceptFromFacing`
- `CollectingNeighborUpdater.java:67` `addAndRun`
- `CollectingNeighborUpdater.java:86` `runUpdates`

## Update Flags

`Block` defines the flags that control side effects:

- `Block.java:92` `UPDATE_NEIGHBORS`
- `Block.java:93` `UPDATE_CLIENTS`
- `Block.java:94` `UPDATE_INVISIBLE`
- `Block.java:95` `UPDATE_IMMEDIATE`
- `Block.java:96` `UPDATE_KNOWN_SHAPE`
- `Block.java:97` `UPDATE_SUPPRESS_DROPS`
- `Block.java:98` `UPDATE_MOVE_BY_PISTON`
- `Block.java:99` `UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE`
- `Block.java:100` `UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS`
- `Block.java:101` `UPDATE_SKIP_ON_PLACE`
- `Block.java:105` `UPDATE_ALL`

## Client Packet Flow

Single-block updates use `ClientboundBlockUpdatePacket`.

- `ClientboundBlockUpdatePacket.java:21` stores `BlockPos`
- `ClientboundBlockUpdatePacket.java:22` stores `BlockState`
- `ClientboundBlockUpdatePacket.java:24` constructor from explicit pos/state
- `ClientboundBlockUpdatePacket.java:29` constructor from level/pos

Section batch updates use `ClientboundSectionBlocksUpdatePacket`.

- `ClientboundSectionBlocksUpdatePacket.java:21` stores `SectionPos`
- `ClientboundSectionBlocksUpdatePacket.java:22` packed section-relative positions
- `ClientboundSectionBlocksUpdatePacket.java:72` `runUpdates(...)`

Client application entry points:

- `ClientPacketListener.java:844` `handleChunkBlocksUpdate`
- `ClientPacketListener.java:930` `handleBlockUpdate`

## Audit Questions

- Does the mutation position represent storage identity, a visible alias, or both?
- Are neighbor updates emitted in the same coordinate space as the storage mutation?
- Can a neighbor update cross a chunk or section boundary?
- Are block update packets reporting storage positions or player-facing positions?
- Are block entity side effects preserved when a block state changes?

## Globe World Notes

Server-side block mutations must enter `Level.setBlock(...)` in canonical coordinates.

Why this matters:

- `ServerChunkCache.getChunk(...)` wraps chunk lookup to canonical chunks.
- Vanilla `Level.setBlock(...)` then passes the same `BlockPos` into `LevelChunk.setBlockState(...)`.
- `LevelChunk.setBlockState(...)` writes local section coordinates with `pos.getX() & 15` and `pos.getZ() & 15`.
- If the chunk is canonical but the `BlockPos` is still an alias coordinate, an alias-side update can mutate the wrong local block in canonical storage.

Project hooks:

- `src/main/java/globe/world/mixin/LevelSetBlockBroadcastMixin.java:34` canonicalizes the server-side `setBlock(...)` position at method entry. Client-side calls keep their packet/view coordinates.
- `src/main/java/globe/world/mixin/LevelSetBlockBroadcastMixin.java:55` captures the old state after the canonical storage mutation.
- `src/main/java/globe/world/mixin/LevelSetBlockBroadcastMixin.java:76` tracks whether vanilla sent a block update.
- `src/main/java/globe/world/mixin/LevelSetBlockBroadcastMixin.java:98` sends a skipped/reentrant server block update with the actual post-update state when vanilla does not send one.

Current status:

- Good: server mutation, dirty marking, neighbor updates, light checks, POI updates, and block-update packets now share the canonical block position.
- Good: packet fanout can treat canonical block updates as the source of truth and relabel to loaded aliases.
- Remaining audit: direct `LevelChunk.setBlockState(...)` calls outside `Level.setBlock(...)`, if any gameplay path uses them, would need separate wrapping or proof that they are already canonical.
- Related worldgen fix: `LevelChunk.postProcessGeneration(...)` can call `Level.setBlock(...)` from alias chunks during chunk promotion. `LevelChunkPostProcessMixin` cancels that pass for non-canonical chunks.
