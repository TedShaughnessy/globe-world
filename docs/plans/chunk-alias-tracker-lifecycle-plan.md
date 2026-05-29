# Chunk Alias Tracker Lifecycle Plan

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

- Scope alias records by dimension or `ServerLevel`.
- Clear records for player disconnect, dimension transfer, respawn into another
  level, world unload, and tiling setting changes.
- Preserve the fast packet-fanout lookup shape.
- Make stale alias state observable during debugging.

## Proposed Implementation

1. Redesign the key structure.
   - Include player UUID, dimension key, canonical chunk, and alias chunk.
   - Prefer dimension key over `ServerLevel` object in the public key to reduce
     accidental level retention.
   - Keep values simple; this is a visibility set, not authority state.

2. Add lifecycle cleanup methods.
   - `clearPlayer(ServerPlayer player)`
   - `clearPlayerDimension(ServerPlayer player, ResourceKey<Level> dimension)`
   - `clearLevel(ResourceKey<Level> dimension)` or level-aware equivalent
   - Optional `clearAll()` for server shutdown/settings reset.

3. Call cleanup from lifecycle mixins.
   - Player disconnect.
   - Dimension change and respawn.
   - Server/world close.
   - Tiling settings change or world reopen path, if settings can change while a
     server is active.

4. Update packet fanout callers.
   - Pass level or dimension context into `aliasesForCanonical`.
   - Update block and light fanout helpers to query dimension-scoped aliases.

5. Add diagnostics.
   - Track total records per player/dimension for debug commands or logs.
   - Log suspicious fanout requests where aliases exist for a different
     dimension.

6. Document the lifecycle.
   - Update `docs/mod-mechanics/chunks.md`.
   - Mention that alias tracking is client-visibility bookkeeping, not world
     authority.

## Validation

- Join, move across aliases, disconnect, reconnect; verify alias state is clean.
- Move through a Nether portal and confirm Overworld aliases do not affect
  Nether block updates.
- Die and respawn in another dimension if possible.
- Unload/reload a world and verify no retained alias entries for the old level.
- On a tiny tile, ensure block update fanout still reaches all visible aliases.

