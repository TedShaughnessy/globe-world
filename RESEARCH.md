# Globe World Research

**Approach:** Infinite-tiling flat world with seamless coordinate wrap at borders — torus topology (or cylinder). No sphere geometry, no gravity changes. Players/entities at `x = maxWorld` teleport to `x = 0` and vice versa (same for Z if bidirectional).

This is a multiplayer mod. All distance/range/tracking systems that operate in world XZ must use **wrapped distance** — `min(|delta|, worldSize - |delta|)` — so players and entities near opposite edges behave as if they are close.

---

## 1. Player Border Teleport

**Class:** `net.minecraft.server.network.ServerGamePacketListenerImpl`

### How server-authoritative teleports work
`handleMovePlayer` receives client position each tick. Key validation:
1. Computes `distSq = dx²+dy²+dz²` from last server-known position
2. If `distSq > 0.0625` AND not creative/spectator/dimension-changing → logs "moved wrongly", sets `movedWrongly = true`
3. If `movedWrongly` AND player new AABB collides with world → calls `teleport(oldX, oldY, oldZ, ...)` to bounce back

**Safe teleport path:** Call `connection.teleport(x, y, z, yRot, xRot)` server-side. This:
- Increments `awaitingTeleport` counter
- Sends `ClientboundPlayerPositionPacket` to client
- Blocks normal movement processing until client ACKs with `ServerboundAcceptTeleportPacket`

This is the same path used by portals — it is safe for border wrapping.

**Risk:** If the mod instead calls `player.setPos(wrappedX, y, z)` without a proper teleport, the next movement packet will have a huge `distSq` and may trigger rejection or bounce-back. **Always use the teleport API.**

---

## 2. Entity Tracking (Multiplayer Critical)

**Class:** `net.minecraft.server.level.ChunkMap$TrackedEntity.updatePlayer(ServerPlayer)`

This decides whether the server sends entity data to a given player. Per-entity per-player every tick.

```java
Vec3 delta = player.position() - entity.position()
double distXZsq = delta.x*delta.x + delta.z*delta.z
double range = min(getEffectiveRange(), playerViewDistance * 16)
if (distXZsq > range*range) → stop tracking (entity disappears for player)
```

**Multiplayer problem:** Player A at `x=5`, Entity B at `x=worldMax-5`. Raw `distXZsq` is huge — entity disappears. With wrapping, they are 10 blocks apart.

**Fix:** Intercept `updatePlayer` via `@Inject` or `@WrapOperation` and replace the `delta.x*delta.x + delta.z*delta.z` calculation with a wrapped XZ distance computation.

---

## 3. Chunk Loading for Spawning

**Class:** `net.minecraft.server.level.ChunkMap`

`playerIsCloseEnoughForSpawning(ServerPlayer, ChunkPos)` gate:
```java
// euclideanDistanceSquared(ChunkPos, Vec3):
double cx = ChunkPos.x * 16 + 8   // chunk center X
double cz = ChunkPos.z * 16 + 8   // chunk center Z
double dx = cx - player.x
double dz = cz - player.z
double distSq = dx*dx + dz*dz
return distSq < 16384.0  // 128² — SPAWN_DISTANCE_BLOCK²
```

**Problem:** Chunks near the opposite edge from the player are never considered for spawning.

**Fix:** Intercept `euclideanDistanceSquared` (private static, use `@WrapOperation` on the `invokestatic` call site inside `playerIsCloseEnoughForSpawning`) to apply wrapped XZ delta.

Also relevant: `getPlayersCloseForSpawning(ChunkPos)` iterates all players and calls `playerIsCloseEnoughForSpawning` — same fix cascades.

`getPlayers(ChunkPos, bool)` — returns players tracking a chunk; also uses chunk-player distance via `DistanceManager`. That system is more complex (distance tickets, level propagation) but same wrapping need.

---

## 4. Mob Spawning

**Class:** `net.minecraft.world.level.NaturalSpawner`

### Position selection
`getRandomPosWithin(Level, LevelChunk)`:
```java
int x = chunkPos.getMinBlockX() + random.nextInt(16)
int z = chunkPos.getMinBlockZ() + random.nextInt(16)
int y = Mth.randomBetweenInclusive(random, level.getMinY(), heightmap(x,z) + 1)
```
Positions are flat chunk coordinates. On a torus, this is fine — chunks are already at the right position in world space.

### Spawn distance gate
`isRightDistanceToPlayerAndSpawnPoint(...)`:
- Rejects if `distSq <= 576` (24 blocks, `MIN_SPAWN_DISTANCE`²) — too close to player
- Rejects if `distSq > 128²` (from `SPAWN_DISTANCE_BLOCK`) — too far
- `distSq = player.distanceToSqr(x+0.5, y, z)` — flat 3D Euclidean

**Problem:** Players near the edge of the world leave a "spawn dead zone" on the other side because raw XZ distance is huge.

**Fix:** Intercept `Player.distanceToSqr` at this call site (or override `isRightDistanceToPlayerAndSpawnPoint`) to use wrapped XZ component in the distance.

**Constants:**
| Constant | Value |
|---|---|
| `MIN_SPAWN_DISTANCE` | 24 blocks |
| `SPAWN_DISTANCE_BLOCK` | 128 blocks |
| `SPAWN_DISTANCE_CHUNK` | 8 chunks |

---

## 5. Mob Despawn

**Class:** `net.minecraft.world.entity.Mob.checkDespawn()`

```java
Player nearest = level.getNearestPlayer(mob, -1.0)  // unlimited range
double distSq = nearest.distanceToSqr(mob)
if (distSq > despawnDistance²) → discard()
if (noActionTime > 600 && rand.nextInt(800) == 0 && distSq > noDespawnDistance²) → discard()
```

`despawnDistance` = 128 (all categories except WATER_AMBIENT = 64).
`noDespawnDistance` = 32 (hardcoded).

**Problem:** Mob 5 blocks past the world edge has huge `distSq` from player 5 blocks inside — instantly despawns.

**Fix:** Intercept `Entity.distanceToSqr(Entity)` or override `checkDespawn` to use wrapped XZ distance. Also `Level.getNearestPlayer` must consider wrap — player "on the other side" may be nearest.

---

## 6. Mob Aggro / Sensing

**Class:** `net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor.doTick()`

```java
double range = entity.getAttributeValue(Attributes.FOLLOW_RANGE)  // default 16
AABB searchBox = entity.getBoundingBox().inflate(range, range, range)
List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, searchBox, ...)
found.sort(by distanceToSqr)
```

**Problem:** An AABB at the edge does not extend through the wrap. A mob at `x=worldMax-5` won't detect a player at `x=5` even though they're 10 blocks apart.

**Fix:** Two options:
- After the normal AABB query, do a second query for the wrapped AABB if the entity is within `range` of an edge, then merge results.
- Intercept `AABB.inflate` and return a union of two AABBs. (Complex — inflate returns a single AABB.)

Simpler: `@Inject` after `doTick`, check if mob is within `range` blocks of any border, run a second wrapped AABB query and merge into the brain memory.

---

## 7. Mob Caps (Multiplayer)

**Class:** `net.minecraft.world.level.LocalMobCapCalculator`

Per-player mob cap tracking. Uses `ChunkPos`-keyed map of players near each chunk. Populated via `ChunkMap` — same chunk-player distance logic as §3.

No direct coordinate math here — it uses `playersNearChunk` which is populated by `ChunkMap`. Fixing §3 cascades here.

**Category caps:**
| Category | Max/chunk | Despawn dist |
|---|---|---|
| MONSTER | 70 | 128 |
| CREATURE | 10 | 128 |
| AMBIENT | 15 | 128 |
| AXOLOTLS | 5 | 128 |
| WATER_CREATURE | 5 | 128 |
| WATER_AMBIENT | 20 | 64 |

---

## 8. Pathfinding

**Class:** `net.minecraft.world.level.pathfinder.Node.distanceTo(Node)`

```java
float dx = node1.x - node0.x
float dy = node1.y - node0.y
float dz = node1.z - node0.z
return sqrt(dx*dx + dy*dy + dz*dz)
```

Also `distanceManhattan`, `distanceToXZ`.

**Assessment:** Paths are short-range (default `FOLLOW_RANGE` = 16 blocks). A mob will never path-find across the wrap boundary because the wrap distance is >> 16. **Low priority.** Edge case: mob standing exactly at the border trying to reach a player on the other side of the wrap — it will fail to find a path. Acceptable for now.

`PathNavigation.getGroundY(Vec3)` reads block below position — fine as long as chunks are loaded.

---

## 9. Block Breaking / Interaction Range

**Class:** `net.minecraft.world.entity.player.Player.isWithinBlockInteractionRange(BlockPos, double buffer)`

```java
double range = blockInteractionRange() + buffer  // default 4.5 + 1.0 = 5.5
AABB blockBox = new AABB(pos)
double distSq = blockBox.distanceToSqr(getEyePosition())
return distSq < range * range
```

**Assessment:** Interaction range is ~5.5 blocks. A player would have to be standing essentially on the border to interact with a block on the other side. **Low priority** — extremely unlikely edge case, and even if it fails the player can step across and interact normally. No fix needed for MVP.

---

## 10. Projectile Motion

**Class:** `net.minecraft.world.entity.projectile.ThrowableProjectile.tick()`,  `net.minecraft.world.entity.projectile.arrow.AbstractArrow.tick()`

Projectiles move by `setPos(position + deltaMovement)` each tick. Gravity: `-Y` always.

**Assessment:** On a torus, gravity is still `-Y`. Projectiles crossing the border just need position wrapping (same as all entities) — if entity X coordinate exceeds world bounds, wrap it. Arrow gravity (0.05/tick²) and throw gravity (0.04/tick²) do not change.

**Fix:** Apply coordinate wrapping in the projectile `tick()` after `setPos` call, or in the base `Entity.tick()` for all entities.

---

## 11. Item Drop / Pickup

Item drops use `applyGravity()` (−Y, 0.04/tick²) — unchanged for torus.

`ItemEntity.playerTouch(Player)` triggered by AABB overlap — no distance check, just bounding-box collision. Pickup happens within ~1 block. **No wrapping needed** unless player/item AABBs straddle the exact border coordinate.

---

## 12. Gravity

`Entity.applyGravity()` always applies `(0, -gravity, 0)`. `LivingEntity.DEFAULT_BASE_GRAVITY` = 0.08. This is **unchanged** for torus wrapping — up is always +Y.

---

## 13. World Border vs Custom Border

`WorldBorder` is the vanilla border system with center/size. Options:
- **Disable WorldBorder entirely** and implement custom border teleport logic
- **Set WorldBorder to exact world dimensions** but override the damage/block behavior so it teleports instead of damaging

Vanilla WorldBorder `isWithinBounds(double x, double z)` is what `ServerGamePacketListenerImpl` uses to validate placement. If the mod's border is larger than WorldBorder, placement near edges will be blocked. Recommend: keep WorldBorder disabled or very large, handle wrapping in movement tick independently.

---

## 14. Movement Validation Summary (Multiplayer)

When server teleports player at border:
1. Server calls `connection.teleport(wrappedX, y, wrappedZ, yRot, xRot)`
2. Server sends `ClientboundPlayerPositionPacket` with new `teleportId`
3. Server sets `awaitingTeleport = teleportId`; movement packets ignored until ACK
4. Client updates position, sends `ServerboundAcceptTeleportPacket(teleportId)`
5. Server clears `awaitingTeleport`, resumes normal movement processing

**No "moved wrongly" spam** — the standard teleport API handles this correctly. Do NOT set position via `player.setPos()` alone.

---

## Priority Injection Points

| System | Class | Method | Priority |
|---|---|---|---|
| Entity position wrap (all entities) | `Entity` | `tick()` or `move()` | **Critical** |
| Player border teleport | `ServerGamePacketListenerImpl` | `handleMovePlayer` | **Critical** |
| Entity tracking distance | `ChunkMap$TrackedEntity` | `updatePlayer` | **Critical** (multiplayer) |
| Chunk spawn distance | `ChunkMap` | `playerIsCloseEnoughForSpawning` / `euclideanDistanceSquared` | **High** |
| Mob despawn distance | `Mob` | `checkDespawn` | **High** |
| Nearest player (despawn) | `Level` | `getNearestPlayer` | **High** |
| Mob aggro near border | `NearestLivingEntitySensor` | `doTick` | **Medium** |
| Spawn dist gate | `NaturalSpawner` | `isRightDistanceToPlayerAndSpawnPoint` | **Medium** |
| Chunk view distance at edges | `ChunkMap$DistanceManager` | ticket propagation | **High** (chunk loading) |

---

## Wrapped Distance Helper (Reference)

```java
// Wrapped XZ distance squared for torus topology
public static double wrappedDistSq(double ax, double az, double bx, double bz, double worldW, double worldD) {
    double dx = Math.abs(ax - bx);
    double dz = Math.abs(az - bz);
    dx = Math.min(dx, worldW - dx);
    dz = Math.min(dz, worldD - dz);
    return dx*dx + dz*dz;
}
```

Y-axis is never wrapped (vertical axis, finite height).
