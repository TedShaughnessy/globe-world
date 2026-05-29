# Worldgen Spillover Lifecycle Plan

## Problem

`WorldGenSpillover` stores pending wrapped worldgen writes in a static map keyed
by `ServerLevel` and canonical chunk. This lets edge features enqueue writes
into canonical chunks that may be applied later, but pending state can survive if
generation is aborted or a level closes before the target chunk applies it.

That creates two risks:

- lost delayed worldgen writes
- retained `ServerLevel` references after unload

## Current Hooks

- `WorldGenRegionMixin` enqueues wrapped writes during worldgen `setBlock`.
- `ChunkGeneratorMixin` applies queued writes around biome decoration.
- `LevelChunkPostProcessMixin` applies queued writes during canonical chunk
  post-processing.
- `PlayerChunkSenderMixin` applies queued writes before sending a chunk packet.

## Goals

- Ensure pending spillover writes cannot retain old levels indefinitely.
- Make lost or stale spillover writes visible.
- Keep canonical chunks as the only persisted worldgen target.
- Preserve edge feature support for canonical chunks.

## Proposed Implementation

1. Add explicit cleanup APIs.
   - `WorldGenSpillover.clearLevel(ServerLevel level)`
   - Optional `clearAll()` for server shutdown.
   - Optional diagnostic return value with number of discarded writes.

2. Call cleanup from level lifecycle hooks.
   - Reuse or mirror the shutdown paths used by `CanonicalChunkTickets`.
   - Clear when `ServerChunkCache` closes or deactivates tickets.
   - Consider server shutdown cleanup if multiple levels can be active.

3. Add stale write diagnostics.
   - Count queued writes by level and canonical chunk.
   - Warn when clearing non-empty pending writes.
   - Add a rate-limited warning if a pending write waits too long in game ticks.

4. Revisit the key shape.
   - If practical, key by dimension/server identity rather than raw `ServerLevel`
     object.
   - If `ServerLevel` is still required for application, keep cleanup mandatory.

5. Confirm application ordering.
   - Audit the current apply sites for chunks generated before their spillover
     writes arrive.
   - Decide whether applying before send is enough or whether chunk load/generation
     completion needs another apply hook.

6. Document worldgen spillover behavior.
   - Update `docs/mod-mechanics/worldgen.md`.
   - Keep the plan only for remaining lifecycle and stale-write diagnostics until
     fully implemented.

## Validation

- Generate features across each tile edge and confirm writes land in canonical
  chunks.
- Stop the world during active generation and confirm pending writes are cleared.
- Watch logs for stale-write warnings during normal generation; there should be
  none in stable cases.
- Stress a tiny tile with high feature density.
- Confirm no retained `ServerLevel` entries after world unload.

