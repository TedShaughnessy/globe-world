# Chunk Alias Tracker Lifecycle Plan

Status: implemented. Durable behavior is documented in
[../mod-mechanics/chunks.md](../mod-mechanics/chunks.md).

## Problem

`ChunkAliasTracker` records loaded aliases by player UUID and canonical chunk.
It does not include the dimension or server-level identity, and it has no clear
cleanup path for disconnects, dimension changes, world unloads, or settings
changes.

Block, block-entity, section, and future light fanout depend on this tracker, so
stale alias entries can send packets to the wrong visible position or accumulate
over time.

## Current Hooks

- `PlayerChunkSenderMixin` adds aliases when chunk packets are sent.
- `PlayerChunkSenderMixin` removes aliases when chunk drop packets are sent.
- `BlockPacketUtil` queries aliases by player and canonical chunk.
- `CanonicalChunkTickets` has level-scoped cleanup, but `ChunkAliasTracker` does
  not.

## Goals

- Scope alias records by player UUID and dimension.
- Clear records for player disconnect, dimension transfer, respawn into another
  level, world unload, and tiling setting changes.
- Preserve the fast packet-fanout lookup shape.
- Make stale alias state observable during debugging.

## Concrete Implementation Plan

### 1. Redesign `ChunkAliasTracker`

Use dimension-scoped player keys and keep the lookup optimized for packet
fanout:

- Store records as:
  - `PlayerDimensionKey(UUID playerId, ResourceKey<Level> dimension)`
  - canonical chunk key: `long ChunkPos.pack(canonicalX, canonicalZ)`
  - alias chunk set: packed alias chunk longs
- Prefer `ResourceKey<Level>` over `ServerLevel` in tracker keys so alias
  visibility records do not retain unloaded levels.
- Keep values as a set of visible aliases. The tracker must not become world
  authority or saved state.

Target public API:

- `addAlias(ServerPlayer player, ResourceKey<Level> dimension, int canonicalX, int canonicalZ, int aliasX, int aliasZ)`
- `removeAlias(ServerPlayer player, ResourceKey<Level> dimension, int canonicalX, int canonicalZ, int aliasX, int aliasZ)`
- `aliasesForCanonical(ServerPlayer player, ResourceKey<Level> dimension, int canonicalX, int canonicalZ)`
- `clearPlayer(ServerPlayer player)`
- `clearPlayerDimension(ServerPlayer player, ResourceKey<Level> dimension)`
- `clearLevel(ResourceKey<Level> dimension)`
- `clearAll()`

Return immutable snapshots from query methods so callers cannot mutate the
tracker while broadcasting packets.

### 2. Update Existing Send And Fanout Paths

- In `PlayerChunkSenderMixin.sendChunk(...)`, pass the method-local
  `ServerLevel level` dimension into `ChunkAliasTracker.addAlias(...)`.
- In `PlayerChunkSenderMixin.dropChunk(...)`, pass `player.level().dimension()`
  into `removeAlias(...)`. Dimension-transfer cleanup below is responsible for
  removing old-dimension aliases if vanilla drops arrive after the player level
  has changed.
- In `BlockPacketUtil`, pass `viewer.level().dimension()` into every
  `aliasesForCanonical(...)` call.
- Keep the no-record fallback behavior unchanged: fall back to nearest virtual
  position when no loaded alias record exists.

### 3. Add Player Lifecycle Cleanup

Add a `PlayerList` lifecycle mixin, or extend
`PlayerListCanonicalPositionMixin` if the file remains readable:

- Inject at `PlayerList.remove(ServerPlayer)` `HEAD` and call
  `ChunkAliasTracker.clearPlayer(player)`.
  - Covers disconnect, kick, duplicate-login removal, and shutdown player
    removal through `PlayerList.removeAll()`.
- Inject at `PlayerList.respawn(ServerPlayer, boolean, Entity.RemovalReason)`
  `HEAD` and call `ChunkAliasTracker.clearPlayer(serverPlayer)`.
  - Respawn creates a replacement `ServerPlayer` with the same UUID, so stale
    aliases for the old instance must be gone before the new instance starts
    receiving chunks.

Add a `ServerPlayer` dimension-transfer mixin:

- Inject at `ServerPlayer.teleport(TeleportTransition)` `HEAD`.
- Compare `((ServerPlayer)(Object)this).level().dimension()` with
  `transition.newLevel().dimension()`.
- If the dimensions differ, call
  `ChunkAliasTracker.clearPlayerDimension(player, oldDimension)`.
- Do not clear aliases for same-dimension teleports; those are ordinary
  movement within the same client chunk space.
- If new mixin classes are added, register them in
  `src/main/resources/globe-world.mixins.json`.

Vanilla source anchors checked in the local Loom cache:

- `PlayerList.remove(...)` handles disconnect-side player removal.
- `PlayerList.respawn(...)` removes the old player and constructs a replacement.
- `ServerPlayer.teleport(TeleportTransition)` performs live dimension transfer
  and calls `setServerLevel(newLevel)` after removing the player from the old
  level.

### 4. Add Level And Server Cleanup

- Extend `ServerChunkCacheMixin` cleanup hooks:
  - `deactivateTicketsOnClosing` `HEAD`
  - `close` `HEAD`
- In both hooks, call `ChunkAliasTracker.clearLevel(this.level.dimension())`
  next to `CanonicalChunkTickets.clearLevel(this.level)`.
- Extend `MinecraftServerMixin` with a `stopServer` cleanup hook that calls
  `ChunkAliasTracker.clearAll()`.
  - This covers same-dimension static records across integrated-server reopen
    paths and any missed per-level cleanup.
- If tiling settings become mutable while a server is active, settings reload
  must also call `clearAll()` before new chunk sends repopulate visibility.

Vanilla source anchors checked in the local Loom cache:

- `ServerChunkCache.deactivateTicketsOnClosing()` runs during server shutdown
  while chunk work is draining.
- `ServerChunkCache.close()` closes chunk-map/light/save resources.
- `MinecraftServer.stopServer()` saves/removes players, drains chunk work, saves
  chunks, and closes levels.

### 5. Add Diagnostics

Add low-noise tracker diagnostics before wiring debug commands:

- `totalAliasRecords()`
- `aliasRecordsForPlayer(ServerPlayer player)`
- `aliasRecordsForPlayerDimension(ServerPlayer player, ResourceKey<Level> dimension)`
- cleanup methods should return the number of removed alias records.
- Log at debug level when a cleanup removes non-zero records.

For suspicious lookups, avoid noisy per-packet logs. If
`aliasesForCanonical(...)` misses in the requested dimension but the same player
has aliases for the same canonical chunk in another dimension, expose that count
through diagnostics or rate-limit a debug log.

### 6. Document The Implemented Lifecycle

When implemented, update `docs/mod-mechanics/chunks.md`:

- Alias tracking is client-visibility bookkeeping.
- Records are scoped by player and dimension.
- Records are populated by chunk sends, removed by chunk drops, and force-cleared
  across disconnect, respawn, dimension transfer, level close, and server stop.
- Alias tracking is not canonical world storage and is not persisted.

## Implementation Sequence

1. Change `ChunkAliasTracker` storage and add the new public API.
2. Update `PlayerChunkSenderMixin` and `BlockPacketUtil` call sites.
3. Add player, dimension-transfer, level-close, and server-stop cleanup hooks.
4. Add diagnostic counters and cleanup debug logs.
5. Compile, then run the manual validation scenarios.
6. Move the durable behavior summary into `docs/mod-mechanics/chunks.md`.

## Validation

- Join, move across aliases, disconnect, reconnect; verify alias state is clean.
- Move through a Nether portal and confirm Overworld aliases do not affect
  Nether block updates.
- Die and respawn in another dimension if possible.
- Unload/reload a world and verify no retained alias entries for the old level.
- On a tiny tile, ensure block update fanout still reaches all visible aliases.
- Use tracker diagnostics after disconnect, respawn, dimension transfer, and
  server stop; expected record count is zero for the cleared scope.
