# Client Canonical Chunk Cache

## Goal

Tiny canonical tiles can make chunk loading feel slow because the client still waits for every visible alias chunk to arrive as its own relabeled full chunk packet. For a 6-chunk tile, most visible chunks are repeats of canonical chunks the client may already have received.

Add an optional client-side cache that can paint an alias chunk immediately from cached canonical chunk data, then let the real server packet overwrite it when it arrives.

## Scope

This should be a client visual/load-latency optimization, not a new authority model.

- The server remains authoritative.
- Alias chunks should still receive normal server chunk packets.
- Cached alias chunks are temporary warm starts.
- Full chunk packets, block updates, section updates, block entity updates, and light updates must continue to apply at visible alias coordinates.

Avoid trying to suppress server sends in the first version. Vanilla chunk sending is server-driven through `PlayerChunkSender`, and this mod already relabels canonical chunks to alias positions there.

## Proposed MVP

1. Add a dev toggle, probably next to the existing debug key handling in `KeyboardHandlerMixin`.
2. Cache received full chunk data by canonical chunk key.
3. When a requested/rendered alias chunk is missing but its canonical chunk data is cached, synthesize a `LevelChunk` at the alias position.
4. Mark the alias chunk and nearby sections dirty so the renderer notices it.
5. When the real `ClientboundLevelChunkWithLightPacket` arrives, apply it normally and refresh the cache.
6. Clear cached data on world unload, dimension change, settings change, or when the toggle is disabled.

## Important Constraints

- Do not share one `LevelChunk` object between aliases. Client chunks are position-keyed, and light, block entities, tint caches, and renderer invalidation depend on the visible chunk position.
- Prefer caching packet/snapshot data, then replaying it into a fresh alias-position chunk.
- Cached data can be stale. The real server packet must always win.
- Block entity tags need careful handling because full chunk packet data stores block entity positions relative to the chunk, while later block entity update packets use absolute block positions.
- Light data is also position-keyed on the client. Cached light must be applied to the alias chunk position, not the canonical position.

## Complexity

MVP complexity is medium. The simple terrain path is straightforward, but correctness lives in the edges:

- packet data reuse and buffer lifetime
- light application
- block entity reconstruction
- renderer invalidation
- cache clearing across world/dimension/settings transitions

Expected effort:

- 1-2 days for a dev-toggle prototype that improves perceived loading.
- 3-5 days for a robust version with debug counters, careful invalidation, and block entity/light audits.

## Success Criteria

- On a tiny tile world, aliases appear quickly after their canonical chunk has been seen once.
- Real server chunk packets still replace cached aliases.
- Block changes and block entity updates still fan out to all visible aliases.
- Toggling the cache off restores current behavior without requiring a restart.
- The debug HUD or logs can show cache hits, misses, and stale replacements during development.
