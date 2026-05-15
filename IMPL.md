# Implementation List


## Phase 1 — Core Repeating World
Define world period W in chunks (e.g. W_CHUNKS = 128, W_BLOCKS = 2048).
Canonical zone: `[-W/2, W/2)` in chunk coords. All server chunk access wraps into this zone.

Coordinate utilities (`CoordUtil`):
- `wrapChunk(c)` — maps any chunk coord into canonical zone via `floorMod`
- `wrapBlock(b)` — same for block coords
- `virtualChunk(canonical, playerChunk)` — inverse: nearest virtual tile position to player
- `virtualBlock(canonical, playerBlock)` — same for block/entity coords

Mixins:
- `ServerChunkCacheMixin` — hooks `getChunk` / `getChunkNow`, wraps x/z to canonical before lookup
- `PlayerChunkSenderMixin` + `LevelChunkPacketAccess` — relabels outbound `ClientboundLevelChunkWithLightPacket` x/z to player's virtual frame; same for `ClientboundForgetLevelChunkPacket`

Test: player walks past tile edge → chunks repeat seamlessly, no client mod required.


## Phase 2 — Seamless Terrain
Modify noise functions to be periodic along both axes (period = W_BLOCKS).
Wrap biome selection coords before lookup.
Wrap structure seeds so villages/temples repeat correctly.
Optional: blend edges to suppress any residual artifacts.

Test: walk tile edge in all directions, no visible seam in terrain, biomes, or structures.


## Phase 3 — Entity Duplication (Multiplayer)
Entities live exclusively in canonical coordinate space. Per-player, outbound entity packets
are translated to the player's virtual frame so each player sees entities at the expected
position in their tile copy.

### Wrapped tracking range
Entity tracker uses Euclidean chunk distance — must be replaced with wrapped distance:
```java
int wrappedDx = Math.min(Math.abs(dx), W_CHUNKS - Math.abs(dx));
int wrappedDz = Math.min(Math.abs(dz), W_CHUNKS - Math.abs(dz));
```
Hook in `ChunkMap` or `ServerEntity` tracking range check.
Without this, entities outside `[-trackingRange, trackingRange]` in canonical coords
are never sent to players in adjacent virtual tiles.

### Per-player entity packet translation
Two packet types carry absolute positions and require per-player coord translation:

| Packet | Field | Action |
|---|---|---|
| `ClientboundAddEntityPacket` | x, z (double) | translate to player's virtual frame |
| `ClientboundTeleportEntityPacket` | x, z (double) | translate to player's virtual frame |
| `ClientboundMoveEntityPacket` | dx, dz (short delta) | no change — delta is frame-independent |

Translation formula (same as `virtualChunk` extended to block coords):
```java
double virtualX = canonical + Math.round((playerVirtualX - canonical) / W_BLOCKS) * W_BLOCKS;
```

Use `@Redirect` on `conn.send` in the entity tracking send path, same pattern as
`PlayerChunkSenderMixin`. Accessor mixin to expose private final x/z fields on each packet.

### Mob cap and spawning
Spawning and mob cap checks operate on canonical chunks via `ServerChunkCacheMixin`.
No additional work — correct by default.

### Player coordinate overflow
Players accumulate large virtual coordinates over long sessions. No forced border teleport.
On death or world load: rebase player to canonical equivalent position (`wrapBlock` on x/z).
Preserves continuity during play; resets coord magnitude at natural breakpoints.

Test: two players in different virtual tile copies see each other and shared mobs correctly.
Mob aggro and pathfinding work within canonical tile; mobs do not need to cross tile boundary.


## Phase 4 — Cosmetic Enhancements
Curvature shader: bend horizon to simulate globe surface.
Optional distance fog or edge vignette.
Test from high altitude, normal FOV, and multiple players simultaneously.


## Phase 5 — Future Extensions
Hexagonal tiling mode for terrain generation.
Alternate world tile sizes.
Additional planetary features (gravity variation, biome cycles).


## Phase 6 — Packaging & Distribution
Remove debug logs. Build `.jar` via Gradle.
Populate `fabric.mod.json` with name, version, dependencies.
Publish to GitHub; optionally CurseForge.
