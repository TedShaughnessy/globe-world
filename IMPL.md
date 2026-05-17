# Implementation Plan

World period: `W_CHUNKS` chunks, `W_BLOCKS = W_CHUNKS * 16` blocks.
Canonical zone: `[-W/2, W/2)` in chunk coords (centered at origin so spawn has full `W/2` radius before wrapping).

---

## Phase 1 — Core Repeating World ✓ Done

**Coordinate utilities (`CoordUtil`):**
- `wrapChunk(c)` — `floorMod` into canonical zone
- `wrapBlock(b)` — same for block coords
- `virtualChunk(canonical, playerChunk)` — nearest virtual tile to player (exists, unused in send path — see below)

**Mixins:**
- `ServerChunkCacheMixin` — hooks `getChunk` / `getChunkNow`, wraps x/z to canonical before lookup
- `PlayerChunkSenderMixin` — `@WrapOperation` on `new ClientboundLevelChunkWithLightPacket(...)` inside static `sendChunk`; loads canonical chunk data; sends packet with virtual coord = raw coord (see below); same for `ClientboundForgetLevelChunkPacket` in `dropChunk`
- `ClientboundLevelChunkWithLightMixin` — stores virtual x/z override; `@Redirect` on GETFIELD for x/z in `write()` to emit virtual coords on the wire

See DEVLOG.md for full architecture rationale (canonical-zone centering, why server relabeling beats client wrapping, why raw-pos = virtual-pos).

**Known pitfalls fixed in PlayerChunkSenderMixin:**

*1. Non-deterministic terrain (getChunkNow returns null)*
Canonical chunks are `W_CHUNKS` away from the player's virtual/raw position and get no natural player ticket, so `getChunkNow(wcx, wcz)` is a lottery. Fix: fall back to `level.getChunk(wcx, wcz)` (forced sync load on server thread).

*2. Multiple aliases coexist naturally — no explicit extra-send needed*
The server generates actual Minecraft chunks at every raw position within view distance. Multiple raw positions that map to the same canonical (e.g. raw `x=12` and raw `x=36` both wrapping to canonical `x=-12`) are generated and tracked independently by the server's chunk holder system. Each alias is sent to the client at its raw position as the virtual coord. Multiple aliases of the same canonical coexist on the client simultaneously — the client treats them as distinct positions. Each alias has its own load/drop lifecycle; no manual deduplication or extra-send logic is needed or correct (deduplicating causes aliases to vanish as the player moves, since the client silently evicts chunks that drift outside `viewDist`).

*3. Canonical chunks unloaded mid-session (distance manager gap)*
Server distance manager uses raw player position. Canonical chunks far from that position accumulate no player ticket and get unloaded. Fix: `addTicketWithRadius(TicketType.FORCED, canonPos, 0)` when a non-canonical chunk is sent; `removeTicketWithRadius(...)` when it is dropped. Refcount tracks all active raw aliases across all players — ticket released only when all aliases drop.

**MC 26.1.2 ticket API (changed from older versions):**
`TicketType` is now a non-generic `Record` — no `create(String, Comparator)` factory, no type parameter.
Use `ServerChunkCache.addTicketWithRadius(TicketType, ChunkPos, int radius)` / `removeTicketWithRadius(...)`.
`TicketType.FORCED` (radius=0) keeps exactly the target chunk at entity-ticking status (equivalent to `/forceload`); note it persists to disk.

---

## Phase 2 — Seamless Terrain

Make noise periodic along both axes with period `W_BLOCKS`.
- Wrap X/Z inputs to noise functions before evaluation
- Wrap biome selection coords before lookup
- Wrap structure placement seeds so structures repeat correctly
- Optional: edge blending for any residual artifacts

Test: walk tile edge in all directions — no visible seam in terrain, biomes, or structures.

---

## Phase 3 — Entity Multiplayer

Entities live exclusively in canonical coordinate space (`[-W/2*16, W/2*16)`). Per-player, outbound entity packets are translated to the player's virtual frame.

Player coordinates grow unboundedly during a session (no border teleport). On death or world load, rebase player to canonical equivalent: `wrapBlock(x)`, `wrapBlock(z)`.

### 3a — Entity packet translation

Outbound packets carrying absolute entity positions require per-player virtual frame translation. Formula (same as `virtualBlock`):
```java
double virtualX = canonical + Math.round((playerVirtualX - canonical) / W_BLOCKS) * W_BLOCKS;
double virtualZ = canonical + Math.round((playerVirtualZ - canonical) / W_BLOCKS) * W_BLOCKS;
```

| Packet | Fields | Action |
|---|---|---|
| `ClientboundAddEntityPacket` | x, z (double) | translate to player virtual frame |
| `ClientboundTeleportEntityPacket` | x, z (double) | translate to player virtual frame |
| `ClientboundMoveEntityPacket` (.Pos / .PosRot) | dx, dz (short delta) | no change — delta is frame-independent as long as entity canonical coords never jump |

Intercept via `@Redirect` on `conn.send` in the entity tracking send path, same pattern as `PlayerChunkSenderMixin`. Accessor mixin to expose `private final` x/z fields on each packet class.

### 3b — Entity tracking range (wrapped distance)

`ChunkMap$TrackedEntity.updatePlayer` decides whether server sends entity to player:
```java
// current (broken near tile boundary):
double distXZsq = delta.x*delta.x + delta.z*delta.z
// replace with:
double dx = wrappedDelta(player.x, entity.x, W_BLOCKS)
double dz = wrappedDelta(player.z, entity.z, W_BLOCKS)
double distXZsq = dx*dx + dz*dz
```
Where `wrappedDelta(a, b, size) = Math.min(Math.abs(a-b), size - Math.abs(a-b))`.

Without this: entities near the canonical zone boundary are never sent to players in adjacent virtual tiles.

### 3c — Block update packets for virtual viewers

`ClientboundBlockUpdatePacket` and `ClientboundSectionBlocksUpdatePacket` carry `BlockPos` / `SectionPos`. Players viewing virtual chunks must receive these with virtual-frame coordinates, not canonical.

Intercept the block update send path and remap position to player's virtual frame using `virtualBlock`.

### 3d — Mob spawning (wrapped spawn eligibility)

`ChunkMap.playerIsCloseEnoughForSpawning(ServerPlayer, ChunkPos)` calls private static:
```java
// net.minecraft.server.level.ChunkMap
euclideanDistanceSquared(ChunkPos, Vec3)  // threshold: 16384.0 = 128²
```
This uses raw XZ delta — players near the tile edge never spawn mobs in the adjacent virtual tile.

Fix: `@WrapOperation` on the `invokestatic euclideanDistanceSquared` call site inside `playerIsCloseEnoughForSpawning`. Replace with wrapped XZ distance squared.

`NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint` also checks player distance (min 24 blocks, max 128 blocks) via `Player.distanceToSqr(x, y, z)`. Same wrapped-delta fix at that call site.

### 3e — Mob despawn (wrapped despawn distance)

`Mob.checkDespawn()`:
```java
Player nearest = level.getNearestPlayer(mob, -1.0)   // unlimited search radius
double distSq  = nearest.distanceToSqr(mob)           // raw Euclidean
// hard despawn at despawnDistance² (128² for most categories)
// soft despawn at noDespawnDistance² (32²)
```

Two fixes needed:
1. `Level.getNearestPlayer` — must compare candidates using wrapped XZ distance so the truly nearest player across the tile boundary is found
2. `distanceToSqr` call in `checkDespawn` — replace with wrapped XZ distance

Without these: mobs near the tile edge see no nearby players (raw distance ≈ `W_BLOCKS`) and instantly hard-despawn.

### 3f — Mob aggro / sensing (wrapped sensing box)

`NearestLivingEntitySensor.doTick(ServerLevel, T)`:
```java
double range = entity.getAttributeValue(Attributes.FOLLOW_RANGE)  // default 16 blocks
AABB box = entity.getBoundingBox().inflate(range, range, range)
List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box, ...)
```

AABB doesn't wrap at tile edge — mob near boundary never detects players in adjacent virtual tile.

Fix: after the normal AABB query, if the mob is within `range` blocks of any tile edge, issue a second query against the wrapped equivalent AABB and merge results into the brain memory. Use `@Inject` at tail of `doTick`.

### 3g — Pathfinding

Mobs do not path across the tile boundary. A mob whose target is in an adjacent virtual tile will fail to find a path at the boundary and stand still or wander. When the mob itself reaches the boundary (chasing a player, wandering), `ServerChunkCacheMixin` handles canonical chunk lookup transparently — the mob walks into the "same" chunk content from the other side.

This is acceptable for MVP. Full cross-boundary pathfinding requires:
- Virtual node coordinates in `WalkNodeEvaluator` (wrap block reads: `level.getBlockState(nodeX % W, y, nodeZ % D)`)
- Path coordinate rebasing after the mob crosses the boundary

Skip for Phase 3; revisit if gameplay is noticeably broken.

---

## Phase 4 — Cosmetic Enhancements

Curvature shader: bend horizon to simulate globe surface.
Optional distance fog or edge vignette.
Test from high altitude, normal FOV, and multiple players simultaneously.

---

## Phase 5 — Future Extensions

Hexagonal tiling mode.
Alternate world tile sizes.
Additional planetary features (biome cycles, atmosphere effects).

---

## Phase 6 — Packaging

Remove debug logs. Build `.jar` via Gradle.
Populate `fabric.mod.json` with name, version, dependencies.
Publish to GitHub; optionally CurseForge/Modrinth.
