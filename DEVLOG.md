# GlobeWorld Dev Log

## Phase 1 — Core Repeating World

### Goal
Player walks past the edge of a finite world tile and terrain repeats seamlessly.
No client mod required. Vanilla mechanics unaffected.

---

### Architecture Overview

The world has a canonical zone of `[-W/2, W/2)` in chunk coordinates
(default `W_CHUNKS = 128`, so `[-64, 64)`). Any chunk request outside this
zone is transparently redirected to the equivalent chunk inside it.

Three layers of interception are required:

```
Player at virtual (131, 0)
        │
        ▼
ServerChunkCache.getChunk(131, 0)      ← ServerChunkCacheMixin wraps to (3, 0)
        │                                  server loads/generates canonical chunk
        ▼
PlayerChunkSender.sendChunk(...)       ← PlayerChunkSenderMixin relabels packet
        │                                  x=3 → x=131 before sending to client
        ▼
Client stores chunk at (131, 0)        ← no client changes needed
```

---

### Components

#### `GlobeConfig`
Defines the world period. `W_CHUNKS = 128` → `W_BLOCKS = 2048`.
Change `W_CHUNKS` here to resize the world tile.

#### `CoordUtil`
Three coordinate utilities:

- `wrapChunk(c)` — maps any chunk coordinate into `[-W/2, W/2)` using
  `floorMod`. Used by the server to find the canonical chunk for any virtual
  request. Canonical zone is centered at 0 so spawn at (0,0,0) sits at the
  tile center with no wrapping at normal view distances.

- `wrapBlock(b)` — same but for block coordinates. Used in Phase 2+ for
  noise/biome seeding.

- `virtualChunk(canonical, playerChunk)` — inverse of `wrapChunk`. Given a
  canonical chunk coord and the player's current chunk coord, returns the
  virtual tile position nearest to the player. Used when relabeling outbound
  packets so the client receives chunks at the position it expects.
  Formula: `canonical + round((playerChunk - canonical) / W) * W`

#### `ServerChunkCacheMixin`
Hooks `ServerChunkCache.getChunk` and `getChunkNow`. When any server-side code
requests a chunk outside `[-W/2, W/2)`, the mixin transparently redirects the
call to the canonical equivalent. This covers all server paths: block access,
mob AI, redstone, chunk generation.

The canonical chunk is loaded and generated normally. The virtual position is
never written to disk.

#### `PlayerChunkSenderMixin` + `LevelChunkPacketAccess`
The server sends chunk data to the client via `ClientboundLevelChunkWithLightPacket`.
The packet embeds the chunk's canonical position (e.g. `x=3`). Without
intervention the client stores it at `(3, 0)` but tries to render it at
`(131, 0)` — a miss.

`PlayerChunkSenderMixin` redirects the `conn.send(packet)` call inside the
private static `sendChunk` method and rewrites `x`/`z` to the virtual position
before the packet leaves the server. The client then stores the chunk exactly
where it expects it. No client-side mixin needed.

`LevelChunkPacketAccess` is a Mixin accessor interface that exposes setters for
the `private final int x` and `private final int z` fields on the packet. Mixin
removes the `final` modifier at bytecode level.

The same mixin also redirects `dropChunk` to translate `ClientboundForgetLevelChunkPacket`
positions to virtual coords, so chunk unloading on the client stays consistent
with how the chunks were originally sent.

---

### Key Design Decisions

**Canonical zone `[-W/2, W/2)` not `[0, W)`**
With `[0, W)` the player spawning at (0,0,0) sits at the corner of the tile.
Any view distance chunk in the negative direction immediately wraps, causing
chunks to appear at far positions. Centering at 0 gives the player a full
`W/2`-chunk radius before wrapping starts.

**Server relabeling over client wrapping**
An earlier approach used a `ClientChunkCacheMixin` to wrap chunk lookups on the
client. This failed because:
1. `ClientChunkCache` uses power-of-2 modular hashing internally. Virtual
   positions that differ by a multiple of the storage array size hash to the
   same slot, causing canonical and virtual chunks to overwrite each other.
2. The server's `ForgetLevelChunkPacket` (chunk unload) carries the canonical
   position, not the virtual one. With client wrapping the unload packet
   translated to the wrong cache slot and chunks were never evicted, corrupting
   the watch state over time.

Relabeling on the server avoids both issues: the client sees a consistent
virtual address space with no hash collisions, and unload packets match the
addresses used at send time.

**`@Redirect` on `conn.send` inside `sendChunk`**
`sendChunk` is a private static method. The canonical chunk is already resolved
by the time it reaches this method, so the virtual position must be recomputed
from the player's current chunk position using `virtualChunk()`. Injecting at
`conn.send` is the last safe point before the packet is serialised and queued.

---

### Known Limitations (Phase 1)

- **Terrain seams**: noise functions are not yet periodic. Edges of the tile
  will have visible seams. Fixed in Phase 2.
- **Structure seams**: villages and other structures that straddle the tile
  boundary will be truncated or duplicated. Fixed in Phase 2.
- **Entity coordinate overflow**: entities at large virtual coordinates carry
  those coordinates in their NBT and network packets. Long play sessions far
  from origin may accumulate floating-point precision issues. Addressed in
  Phase 3 (player rebasing).
- **`virtualChunk` approximation on unload**: `dropChunk` computes the virtual
  position from the player's position at unload time, which may differ slightly
  from their position when the chunk was sent. For normal movement speeds this
  is a non-issue; edge cases exist during rapid teleportation.
