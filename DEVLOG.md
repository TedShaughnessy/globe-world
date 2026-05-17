# GlobeWorld Dev Log

## Phase 1 — Core Repeating World

### Goal
Player walks past the edge of a finite world tile and terrain repeats seamlessly.
No client mod required. Vanilla mechanics unaffected.

---

### Architecture Overview

The world has a canonical zone of `[-W/2, W/2)` in chunk coordinates
(`W_CHUNKS = 24`, so `[-12, 11]`). Any chunk request outside this zone is
transparently redirected to the equivalent chunk inside it.

Three layers of interception:

```
Player at raw chunk (22, -38)
        │
        ▼
ServerChunkCache.getChunk(22, -38)     ← ServerChunkCacheMixin wraps to (-2, 10)
        │                                  server loads/generates canonical chunk
        ▼
PlayerChunkSender.sendChunk(...)       ← PlayerChunkSenderMixin intercepts packet
        │                                  swaps in canonical data; keeps x=22, z=-38
        ▼                                  as virtual coord (raw pos = virtual pos)
Client stores chunk at (22, -38)       ← no client position wrapping needed
```

The server never teleports the player. Raw coordinates drift freely as the player
walks. `wrapChunk` maps any drifted coordinate back to canonical for data lookup,
and the packet is labelled with the original raw position so the client stores it
in the right place.

---

### Components

#### `GlobeConfig`
Defines the world period. `W_CHUNKS = 24` → `W_BLOCKS = 384`.
Change `W_CHUNKS` here to resize the world tile.

#### `CoordUtil`
- `wrapChunk(c)` — maps any chunk coordinate into `[-W/2, W/2)` using `floorMod`.
  Used by the server to find the canonical chunk for any raw request.
  Canonical zone is centered at 0 so spawn at (0,0,0) sits at tile center with
  no wrapping at normal view distances.

- `wrapBlock(b)` — same for block coordinates. Used in Phase 2+ for noise/biome seeding.

- `virtualChunk(canonical, playerChunk)` — returns the alias of `canonical` nearest
  to `playerChunk`. Present in CoordUtil but **not used in the current send path**
  (see PlayerChunkSenderMixin below).

#### `ServerChunkCacheMixin`
Hooks `ServerChunkCache.getChunk` and `getChunkNow`. When any server-side code
requests a chunk outside `[-12, 11]`, the mixin redirects to the canonical
equivalent. Covers all server paths: block access, mob AI, redstone, generation.

The canonical chunk is loaded and generated normally. Virtual positions are never
written to disk.

#### `PlayerChunkSenderMixin`

Intercepts outgoing chunk packets via `@WrapOperation` on
`new ClientboundLevelChunkWithLightPacket(...)` inside the static `sendChunk`
method, and `new ClientboundForgetLevelChunkPacket(...)` inside `dropChunk`.

**How virtual coords work (raw pos = virtual pos):**

The server generates actual Minecraft chunks at raw positions around the player's
current coordinates. A player at raw chunk `(22, -38)` causes vanilla MC to load
raw chunks in a radius — e.g. raw `(22, -50)`, `(-2, -38)`, `(22, -38)` etc.
Most of these are non-canonical. `PlayerChunkSenderMixin`:

1. Computes `wrapChunk(cx)` / `wrapChunk(cz)` to find the canonical chunk.
2. For non-canonical raw chunks: acquires a FORCED ticket on the canonical chunk
   (keeps it in memory), loads the canonical `LevelChunk`, and serialises its
   block data into the packet.
3. Sends the packet with the virtual coord set to the **raw position** (unchanged).
   The client stores it at the raw position — which is within its `viewDistance`
   ring since the server only sends chunks it normally would.

Drop path (`relabelDropPacket`) simply releases the FORCED ticket and sends
`ClientboundForgetLevelChunkPacket` with the same raw position. Load and drop
coords always match.

**Multiple aliases coexist on the client.**
When `viewDistance > W_CHUNKS / 2` (which is typical for `W_CHUNKS = 24`), more
than one copy of the same canonical chunk falls within view distance. Each raw
alias has an independent load/drop lifecycle managed by the server's normal chunk
radius system. Multiple copies render simultaneously — this is correct and
necessary for the tiling illusion. The client treats each raw position as a
distinct chunk.

**Why `virtualChunk()` is not used for send:**
An earlier approach computed a single "nearest alias" virtual coord using
`virtualChunk(canonical, playerPos)` and sent only that one copy. This broke as
the player moved: the chosen alias would drift outside the client's `viewDist`
window (client silently evicts it, no drop event), and no replacement was sent
because the server-side chunk holder was still alive. Result: persistent holes in
the world. Letting all raw aliases send naturally at their own positions avoids
this entirely.

**FORCED ticket refcounting:**
`FORCED_REFS` is a global `ConcurrentHashMap<Long, AtomicInteger>` keyed on
canonical chunk position. Each non-canonical raw alias increments the refcount
on send and decrements on drop. The FORCED ticket is added on `0→1` and removed
on `1→0`. This keeps the canonical chunk in server memory while any player has
any alias loaded, regardless of how many aliases or players are involved.

#### `ClientboundLevelChunkWithLightMixin`
Stores a virtual x/z override pair as `@Unique` fields on the packet instance.
`@Redirect` on the GETFIELD opcodes for `x` and `z` inside `write()` substitutes
the override values when set. This is how `PlayerChunkSenderMixin` rewrites the
wire position without needing a separate accessor interface or reflection.

---

### Key Design Decisions

**Canonical zone `[-W/2, W/2)` not `[0, W)`**
With `[0, W)` the player spawning at (0,0,0) sits at a tile corner. Any
negative-direction chunk immediately wraps, causing far-position virtual coords
at normal view distances. Centering at 0 gives `W/2` chunks of natural radius
before wrapping starts.

**Server relabeling over client wrapping**
An earlier approach used a `ClientChunkCacheMixin` to wrap chunk lookups on the
client. This failed because:
1. `ClientChunkCache` uses modular hashing. Virtual positions that differ by a
   multiple of the storage array size collide, causing canonical and virtual
   chunks to overwrite each other.
2. `ClientboundForgetLevelChunkPacket` carries the canonical position. With
   client wrapping, unload packets translated to the wrong cache slot — chunks
   were never evicted, corrupting the watch state over time.

Server relabeling avoids both issues: the client sees a consistent virtual address
space with no hash collisions, and unload packets always match the addresses used
at send time.

**`@WrapOperation` on `new ClientboundLevelChunkWithLightPacket`**
`sendChunk` is a private static method. Wrapping the constructor call is the
cleanest injection point: it intercepts packet creation, allows swapping in a
different `LevelChunk` for the data, and returns the relabeled packet in one
operation. The alternative (`@Redirect` on `conn.send`) is harder to type-safely
swap chunk data at.

---

### Known Limitations (Phase 1)

- **Terrain seams**: noise functions are not yet periodic. Tile edges will have
  visible seams. Fixed in Phase 2.
- **Structure seams**: villages and other structures straddling the boundary will
  be truncated or duplicated. Fixed in Phase 2.
- **Block update packets**: `ClientboundBlockUpdatePacket` and
  `ClientboundSectionBlocksUpdatePacket` carry canonical `BlockPos`/`SectionPos`.
  Players viewing a non-canonical alias tile receive block updates at the wrong
  position and silently discard them. Live block changes (breaks, places, redstone)
  will not animate correctly through a tiled border until this is fixed (Phase 3c).
- **Entity coordinate accumulation**: entities at large raw coordinates carry
  those coords in NBT and network packets. Long sessions far from origin may
  accumulate floating-point precision issues. Addressed in Phase 3 (player rebasing).
