# Border Continuity Design

How to make the teleport border invisible — chunks, entities, projectiles, spawning, and pathfinding all working seamlessly across the seam.

**Assumption:** World width `W` blocks in X, depth `D` blocks in Z. Valid coords `[0, W)` × `[0, D)`. Player at `x = W - ε` teleports to `x = ε`. Same for Z.

---

## Core Concept: Virtual Extended Space

The fundamental technique used throughout this doc.

A player near the border must perceive the world as continuous. We do this by presenting the content on the other side of the border as if it exists in **extended coordinates** beyond the world edge. The client never knows the world is finite.

```
Real world:     [  0 ........... W-1  ]
Extended view:  [  W-vd ........ W-1 | 0 ....... vd  ]  ← player near right edge sees this
                 (real chunks)          (wrapped = real chunks at x=0..vd, sent as x=W..W+vd)
```

When the player crosses and teleports from `x=W` to `x=0`, the visible content has already been loaded — the seam is invisible.

This concept applies to: chunk data, entity positions, projectile positions, spawn regions, despawn regions.

---

## 1. Chunks

### What needs to happen
Player near `x = W - n` (within view distance of the edge) must receive chunk data for `chunkX = 0..n` remapped as `chunkX = W/16..W/16+n`.

### Server side
`PlayerChunkSenderMixin` (already being worked on): intercept chunk send logic. When the server would send chunk `(cx, cz)` to a player but that chunk is in the "virtual overflow" region, load the real wrapped chunk `(cx % worldChunksX, cz % worldChunksZ)` and rewrite the `ChunkPos` in the outbound packet to the virtual coordinate.

**Packet to intercept:** `ClientboundLevelChunkWithLightPacket` — rewrite `x` and `z` fields before send.

### Client side
`ClientboundLevelChunkWithLightMixin` (already being worked on): if incoming chunk `(cx, cz)` has coordinates beyond the real world bounds, accept and render them at those virtual coordinates. The client chunk storage already supports arbitrary chunk coords as long as they are within `viewDistance` of the player.

### Chunk unload
When player moves past the border and teleports, the virtual extended chunks (now behind the player) must be unloaded and the newly visible ones loaded. Standard chunk tick handles this as long as the remapping stays consistent.

### Edge case: chunk updates
Block updates (`ClientboundBlockUpdatePacket`, `ClientboundSectionBlocksUpdatePacket`) also carry `BlockPos` or `SectionPos`. These must be remapped too when sent to players viewing virtual chunks. **This is easy to miss.**

---

## 2. Entity Visibility Across the Border

### Problem
Server decides to track entity→player via `ChunkMap$TrackedEntity.updatePlayer`. Decision: `distXZsq(player, entity) < range²`. Player at `x=W-5`, entity at `x=5`: raw delta = `W-10`, but wrapped delta = `10`. Entity never gets sent.

Entity position packets also carry raw world coordinates. Even if we fix tracking, a client at `x=W-5` receiving an entity at world `x=5` would render it 10 blocks ahead (correct) only if the packet says `x = W+5` (virtual), not `x = 5` (real).

### Fix: two-part

**Part A — Tracking distance (server):**
Intercept `ChunkMap$TrackedEntity.updatePlayer`. Replace the raw `distXZsq` check with wrapped distance:
```java
double dx = wrappedDelta(player.x, entity.x, W)  // min(|delta|, W - |delta|)
double dz = wrappedDelta(player.z, entity.z, D)
double distSq = dx*dx + dz*dz
```

**Part B — Coordinate remapping in packets:**
When sending entity data to a player, if the entity is "across" the border from the player, remap entity coordinates into virtual space:

```java
// entity is at realX, player is at playerX
// If wrapping across X border: virtualX = realX + W (or realX - W)
double virtualX = remapForPlayer(entity.x, player.x, W)
double virtualZ = remapForPlayer(entity.z, player.z, D)
```

Where `remapForPlayer(ePos, pPos, size)`:
- If `ePos - pPos > size/2` → `ePos - size`  (entity is "east" but actually wraps to west of player)
- If `pPos - ePos > size/2` → `ePos + size`  (entity is "west" but wraps to east of player)
- Otherwise → `ePos`

**Packets to intercept (server-side, per-player):**
- `ClientboundAddEntityPacket` — initial spawn (x, y, z)
- `ClientboundMoveEntityPacket.Pos` / `ClientboundMoveEntityPacket.PosRot` — relative move (delta bytes, small so usually fine at border — but absolute teleport packets matter)
- `ClientboundTeleportEntityPacket` — absolute position

The relative move packets use `short` deltas (`δ = (new - old) * 4096`). If entity is teleported by the server for border wrap, the delta will be huge (±`W * 4096`). The server must instead send a full teleport packet with the virtual coordinate.

### Player-to-player visibility
Same fix — players are entities too. `ChunkMap$TrackedEntity` tracks all entities including `ServerPlayer`. Part A + Part B above covers this.

---

## 3. Mob Spawning Across the Border

### What to fix
`NaturalSpawner.spawnCategoryForChunk` only spawns in chunks that `LocalMobCapCalculator` considers "near" a player. That uses `ChunkMap.playerIsCloseEnoughForSpawning` → `euclideanDistanceSquared(ChunkPos, player.position())` with 128-block threshold.

**Fix:** Replace `euclideanDistanceSquared` with a wrapped version:
```java
double dx = wrappedDelta(chunkCenterX, player.x, W)
double dz = wrappedDelta(chunkCenterZ, player.z, D)
return dx*dx + dz*dz
```

`NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint` also checks player distance (minimum 24, max 128). Same wrapped-delta fix on the `player.distanceToSqr(x, y, z)` call.

No special handling needed for spawn position selection — `getRandomPosWithin` picks within a chunk, which already exists in real coordinates.

---

## 4. Mob Despawn Across the Border

`Mob.checkDespawn` uses `Level.getNearestPlayer(mob, -1)` then `player.distanceToSqr(mob)`.

**Fix A:** Override `getNearestPlayer` (or intercept it) to use wrapped XZ distance when comparing candidates.

**Fix B:** Override the `distanceToSqr` call in `checkDespawn` to use wrapped XZ distance.

Without this: a mob 5 blocks past the seam from a player will instantly despawn because the nearest player is (apparent) `W - 10` blocks away.

---

## 5. Pathfinding Across the Border

### MVP approach (recommended first)
Mobs do not path through the border. If a mob's navigation target is on the other side and no path exists (wall of air at world edge), the mob will stand still or wander. When the mob itself crosses the border (chased or wandering), it teleports like any other entity.

This is invisible in practice: mobs path locally, players cross the border and see the other side already loaded (§1), mobs on the other side are visible (§2) and will have local paths.

### Full approach (post-MVP)
For mobs to smoothly path across the border:

1. **Navigation region wrapping:** `PathNavigation.createPath` builds a `PathNavigationRegion` around the mob + target. If the straight-line path crosses a border, extend the region with wrapped chunks.

2. **Virtual node coordinates:** `WalkNodeEvaluator` works in `BlockPos` integers. Nodes past the border would have `x > W` or `x < 0`. The evaluator must treat these as valid by remapping when reading block data: `level.getBlockState(new BlockPos(nodeX % W, nodeY, nodeZ % D))`.

3. **Path execution:** `PathNavigation.tick` moves mob toward next node. If next node has virtual coordinate (e.g., `x = W + 3`), the mob crosses the border and teleports — but its path still points to `x = W + 3`. After teleport the mob is at `x = 3`, path node says `x = W + 3`: delta is `W`, mob overshoots. Need to remap path node coordinates after border teleport.

Full approach is complex. Ship MVP first.

---

## 6. Projectiles Crossing the Border

### Position wrap (server)
In projectile `tick()`, after `setPos(newPos)`, check bounds:
```java
double x = entity.getX()
double z = entity.getZ()
if (x < 0)    x += W
if (x >= W)   x -= W
if (z < 0)    z += D
if (z >= D)   z -= D
entity.setPos(x, entity.getY(), z)
```

This is safe for server-side collision and hit detection — the projectile is always in valid world space.

### Visual continuity (client)
Same problem as entity visibility (§2). A projectile approaching the border from inside will, in the client's view, fly into the virtual extended space and then pop to the real wrapped coordinate. This causes a visual jump.

To fix:
- The server sends the projectile's position in **virtual extended coordinates** to clients who are watching from the "real" side
- When the projectile crosses and its server position wraps, the server sends a new `ClientboundTeleportEntityPacket` with the virtual coordinate relative to each watching client's perspective

In practice, because projectiles move quickly and chunk render distance is already showing the other side, the pop is small or invisible. **For MVP, just do server-side position wrap and accept a one-frame pop.**

### Hit detection
Projectile collision (`ProjectileUtil.getHitResultOnMoveVector`) raycasts `position → position + delta`. If the ray crosses the border mid-flight, the raycast may miss the target.

**Fix:** If `position + delta` would cross the border, clip the ray at the border, check that segment, then continue from the wrapped position for the remainder. This requires splitting the movement step at the border plane.

Simpler MVP: projectiles that cross in a single tick (very fast projectiles) may miss a hit at the border. Acceptable for most gameplay.

---

## 7. Player Interaction and Combat Across the Border

`Player.isWithinBlockInteractionRange` uses `AABB.distanceToSqr(eyePosition)`. A player at `x=W-2` cannot break a block at `x=1` even though wrapped distance is 3 blocks.

**Fix:** Intercept `isWithinBlockInteractionRange`. If wrapped XZ distance < range but raw distance > range, accept the interaction, and remap the `BlockPos` to virtual coordinates for the server's block lookup.

For attacks: `ServerPlayer` validates entity interaction range via wrapped `distanceToSqr`. Same approach — intercept and use wrapped XZ distance.

In practice, view distance shows the other side so the player can see targets; the interaction check is the only blocker.

---

## 8. Summary Table

| System | Difficulty | What to intercept |
|---|---|---|
| Chunk data across border | **Medium** | `PlayerChunkSenderMixin` (already started), `ClientboundLevelChunkWithLightMixin` (already started), also block update packets |
| Entity tracking decision | **Easy** | `ChunkMap$TrackedEntity.updatePlayer` — replace distXZsq |
| Entity position in packets | **Hard** | `ClientboundAddEntityPacket`, `ClientboundTeleportEntityPacket` — remap per-player |
| Relative entity move at border | **Medium** | Detect large delta → send teleport packet with virtual coord instead |
| Mob spawn eligibility | **Easy** | `ChunkMap.euclideanDistanceSquared` (private static, `@WrapOperation`) |
| Mob despawn | **Easy** | `Mob.checkDespawn` — wrap distSqr |
| Nearest player lookup | **Easy** | `Level.getNearestPlayer` — wrap comparison |
| Pathfinding across border | **Hard** | Navigation region + node evaluator (skip for MVP) |
| Projectile position wrap | **Easy** | Inject after `setPos` in entity/projectile tick |
| Projectile visual continuity | **Medium** | Send virtual coords in teleport packets |
| Projectile hit at border | **Hard** | Split raycast at border plane (skip for MVP) |
| Block interaction across border | **Medium** | `Player.isWithinBlockInteractionRange` + BlockPos remap |
| Block update packets to virtual viewers | **Medium** | Intercept `ClientboundBlockUpdatePacket` send path |

---

## Implementation Order (Recommended)

1. **Chunk wrapping** — most visible, already started. Must also handle block update packets.
2. **Entity position wrap** (server tick) — all entities including projectiles wrap at border.
3. **Player border teleport** — `handleMovePlayer` detects out-of-bounds, calls `connection.teleport`.
4. **Entity tracking distance** — wrapped XZ in `updatePlayer`. Entities visible across border.
5. **Entity packet coordinate remapping** — virtual coords in spawn/teleport packets. Entities render in correct position.
6. **Mob spawn/despawn with wrapped distance** — world feels alive near border.
7. **Relative move packet at border** — fix the one-frame pop for moving entities.
8. **Block interaction across border** — QoL, not blocking.
9. **Pathfinding** (optional, post-MVP).
