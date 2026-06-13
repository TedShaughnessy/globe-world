# Minecraft Coordinate Coverage Audit

This audit compares vanilla Minecraft 26.1.2 coordinate-sensitive systems
against Globe World's current wrapping coverage.

It is intentionally an investigation artifact. Durable explanations for
implemented behavior should live in `docs/mod-mechanics/`; entries here should
be removed or moved once the relevant gap is implemented or consciously
declared out of scope.

## Source Anchors

Common/server source jar:

`net/minecraft/minecraft-common-52430b475d/26.1.2/minecraft-common-52430b475d-26.1.2-sources.jar`

Client-only source jar:

`net/minecraft/minecraft-clientOnly-52430b475d/26.1.2/minecraft-clientOnly-52430b475d-26.1.2-sources.jar`

High-value vanilla files inspected during this pass:

- `net/minecraft/world/level/Level.java`
- `net/minecraft/server/level/ServerLevel.java`
- `net/minecraft/server/level/ServerChunkCache.java`
- `net/minecraft/server/level/ChunkMap.java`
- `net/minecraft/world/level/ServerExplosion.java`
- `net/minecraft/world/entity/ai/village/poi/PoiManager.java`
- `net/minecraft/world/level/gameevent/GameEventDispatcher.java`
- `net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java`
- `net/minecraft/world/level/EntityGetter.java`
- `net/minecraft/server/level/ServerEntityGetter.java`
- `net/minecraft/world/level/CollisionGetter.java`
- `net/minecraft/world/level/entity/EntitySectionStorage.java`
- `net/minecraft/world/ticks/LevelTicks.java`
- `net/minecraft/server/commands/*Command.java`

## Coverage Summary

| Vanilla coordinate family | Current coverage | Notes |
| --- | --- | --- |
| Topology arithmetic and dimension policy | Covered | `CoordUtil`, `DimensionTiling`, `TopologyContext`, and `TopologyContexts` are the shared boundary. |
| Server chunk lookup | Covered for runtime lookups | `ServerChunkCacheMixin` canonicalizes `getChunk`, `getChunkNow`, and block-change chunk routing. |
| Chunk packet identity | Covered | Full chunks are sourced from canonical data and sent under alias chunk coordinates; alias tickets keep canonical chunks alive. |
| Block writes through `Level.setBlock(...)` | Covered for server runtime | `LevelSetBlockBroadcastMixin` canonicalizes server write positions before `LevelChunk.setBlockState(...)`. |
| Block update, block entity, light, biome, and section packets | Covered | `BlockPacketUtil` and `ChunkPacketUtil` fan out to loaded aliases. |
| Block entities | Mostly covered | Runtime lookup/removal/dirty marking canonicalize. Persistence and direct worldgen block-entity side effects still need edge-case audit. |
| Scheduled ticks | Covered for main runtime path | `LevelTicksMixin` canonicalizes scheduled block/fluid tick positions. Clone/copy edge cases remain on the test list. |
| Random ticks and precipitation lane | Covered | `ChunkMapRandomTickMixin` runs random ticks from canonical chunks once. |
| Fluids | Mostly covered | Fluid spreading mostly flows through canonical block access and scheduled ticks. Direct or unusual fluid/worldgen side effects still need regression tests. |
| World-event and cosmetic packets | Covered at packet layer | Sounds, particles, level events, block events, block destruction, and explosion packet centers are virtualized per viewer. |
| Player block interaction from aliases | Covered for main paths | Item use, block breaking, sign edit, reach, and mutation permission checks have targeted hooks. |
| Entity storage/canonicalization | Covered for non-player entities | Add, tick, same-dimension teleport, mounted stacks, and packet positions are canonicalized or virtualized. |
| Entity tracking/ticking/spawning/despawn | Mostly covered | Tracking distances, ticking range, natural spawn candidates, natural world-spawn exclusion, default/player spawn search, audited event spawn searches, and despawn distance use wrapped logic. Generic helper callers still need caller-specific review. |
| Mob target selection, sensing, look, attack, and ranged aim | Broadly covered | Targeting conditions, nearest-entity selection, sensors, look controls, melee/ranged goals, and many mob-specific launch paths use actor-local aliases. |
| Projectile server collision | Broadly covered | Shared `ProjectileUtil` paths, arrows/tridents, splash potions, fishing owner/pullback, and curved item validation use topological helpers. |
| Generic entity broad queries | Partial by design | Important callers are wrapped one by one. A global `Level.getEntities(...)` replacement is intentionally avoided because many callers are side-effect-sensitive. |
| Entity collision and movement | Partial | Block collision usually benefits from chunk/block lookup wrapping. Entity-vs-entity collision across seams is not generally topological unless a caller has a targeted hook. |
| World generation terrain/noise | Covered for known terrain modes | Density/noise/surface/biome hooks and scoped dimension tiling cover the main generator sample paths. |
| Worldgen writes and spillover | Partial | `WorldGenRegionMixin`, `GenerationWindow`, and spillover cover direct block state writes. Unobserved replay does not fully reconstruct block entities, scheduled ticks, fluid ticks, or POI side effects. |
| Structures and forced progression | Implemented with audit boundaries | Structure reference/placement shifts and forced stronghold/fortress starts are implemented. Structure query/persistence paths remain open. |
| Nether portals | Covered for configured scale path | Source X/Z canonicalizes before scale conversion; target wraps in the destination dimension. Round-trip tests remain needed. |
| End portal progression | Covered | Forced stronghold and fallback portal paths use canonical ownership. End dimension itself remains untiled. |
| Maps | Covered for pixels and tracked player markers | Static markers keep stored map coordinates. |
| Waypoints | Covered | Block/chunk/azimuth waypoint packets use receiver-nearest aliases and wrapped visibility/range checks. |
| Lodestone compasses | Covered for validation | `LodestoneTrackerMixin` validates the canonical POI. General compass angle behavior is covered client-side. |
| Client chunk/world cache | Covered through packet relabeling | The client remains vanilla-shaped; aliases are separate raw client chunks. |
| Client picking and curvature | Covered for intended interactions | Curved block/entity/item picking feeds server validation. Physics/collision are not curved. |
| Local sky/time gameplay | Covered for documented hooks | Sleep, monster/phantom spawning, undead burning, villager schedules, bees, turtle eggs, clocks, and patrol gates are documented. |
| Commands and admin tools | Mostly raw vanilla policy | Lower-level hooks may canonicalize actual state mutation, but command selection, loaded checks, regions, and output coordinates are not generally topological. |

## High-Risk Open Coordinate Families

### POI, Villages, And Raids

Vanilla `PoiManager` owns an independent coordinate index. Its range searches
use raw chunk ranges and raw distance ordering:

- `PoiManager.getInSquare(...)` scans `ChunkPos.rangeClosed(ChunkPos.containing(center), chunkRadius)`.
- `PoiManager.getInRange(...)` filters with `r.getPos().distSqr(center)`.
- `PoiManager.findClosest(...)` and related helpers sort by raw `distSqr`.
- `PoiManager.sectionsToVillage(...)` uses a section-distance graph keyed by raw `SectionPos`.

Implemented for targeted user-visible gameplay. `TopologicalPoiQueries` scans
canonical POI chunks, dedupes canonical POI identity, and sorts/filters results
by wrapped X/Z distance. Caller mixins cover villager beds/jobs, bee hives, cat
spawning, village navigation, wandering trader meeting points, raid POI refresh,
raider village movement, and lightning-rod targeting while keeping canonical POI
storage and occupancy records.

Current behavior is documented in
[POI And Villages](../mod-mechanics/poi-and-villages.md). Admin/debug POI
output remains raw unless a future command policy explicitly changes it.

### Game Events And Vibrations

Implemented. `GameEventDispatcherMixin` routes enabled dimensions through
`TopologicalGameEvents`, which visits canonical listener sections touched by
the visible event radius, delivers each real listener once, and passes a
listener-local event source into vanilla listener handling. Vibration listeners
therefore use visible-frame distance, occlusion, travel time, and particle
origins while keeping the original `GameEvent.Context` identity.

### Server-Side Explosions

Implemented. `ServerExplosionMixin` canonical-dedupes the block target list
after vanilla ray collection, and `TopologicalExplosions` replaces entity
damage, exposure, and knockback geometry with visible-frame alias calculations
when tiling is enabled. Outbound explosion packets continue to use
receiver-local center relabeling.

Current behavior is documented in
[Explosions](../mod-mechanics/explosions.md).

### Spawn And Respawn Position Search

Implemented for the audited Minecraft 26.1.2 paths that choose or validate
positions before existing canonicalization hooks see the final entity or player
position:

- `PlayerSpawnFinder.findSpawn(...)` and
  `PlayerSpawnFinder.getSpawnPosInChunk(...)` now use canonical tile-bounded
  default player/world spawn search and canonical `SPAWN_SEARCH` chunk tickets.
- `ServerPlayer.findRespawnAndUseSpawnBlock(...)` reads and can mutate bed,
  respawn-anchor, and forced respawn positions from raw saved
  `RespawnConfig` metadata, but canonicalizes the owner block at use time.
- `NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint(...)` uses wrapped
  distance between natural-spawn candidates and world spawn for the vanilla
  24-block exclusion.
- `WanderingTraderSpawner.findSpawnPositionNear(...)` and
  `VillageSiege.findRandomSpawnPos(...)` wrap X/Z height/spawn checks around
  topological references.

`SpawnUtil.trySpawnMob(...)` remains intentionally unwrapped globally. The
known villager golem, creaking-heart, and sculk-shrieker callers start from
canonical storage owners in the current implementation.

Historical plan:
[Topological spawn and respawn search](topological-spawn-and-respawn-search.md).

### Block-Triggered Entity Queries

Many block/block-entity systems query entities from an `AABB` near a block:
pressure plates, detector rails, tripwire, hoppers, conduits, beacons, shulker
boxes, chests blocked by cats, beehives, piston moving blocks, and similar
systems.

Globe World has targeted hooks for several important cases, including container
openers, beacon/conduit-style effect radius, warden warnings, beehive anger,
trial/vault player detection, and some generic player proximity helpers.
However, raw `Level.getEntities(...)` and `getEntitiesOfClass(...)` are not
globally replaced. Any unwrapped block-trigger query near a seam can miss an
entity visible through an alias, or can fail to interact with an alias-local
entity box.

Current coverage and remaining caller policy:
[Entity Query Caller Matrix](../mod-mechanics/entity-query-caller-matrix.md).

### Generic Entity Collision

Block collision checks usually benefit from chunk and block lookup wrapping, but
entity collision broad-phase is keyed by vanilla entity sections and raw `AABB`
queries. Globe World avoids a global replacement because collision callers have
different side-effect and identity expectations.

Known risk areas include minecart/entity pickup, item merging, mob/player
pushes, vehicle placement, armor stand and crystal placement checks, dismount
searches, and moving piston entity displacement across a tile edge.

Current coverage starts with narrow, user-visible cases: item pickup/merge,
minecart pickup/push, pressure plates/detector rails, vehicle placement, and
moving piston displacement. Caller policy and intentionally vanilla cases live
in [Entity Query Caller Matrix](../mod-mechanics/entity-query-caller-matrix.md).

### Commands And Admin Coordinate Regions

Command code uses raw vanilla coordinates heavily. Examples include `fill`,
`clone`, `place`, `locate`, `forceload`, `spawnpoint`, `setworldspawn`,
`teleport`, `summon`, and selectors. The lower-level block/entity hooks may
canonicalize the final state access, but command region iteration, loaded
checks, command success messages, and selected coordinates remain raw unless a
specific command has a Globe World hook.

Implemented policy:
[Commands And Admin Coordinates](../mod-mechanics/commands.md).

## Medium-Risk Open Coordinate Families

### Direct Chunk And Section Mutation

Runtime gameplay mostly enters through `Level.setBlock(...)`, but vanilla has
direct chunk/section mutations in worldgen and a few special paths:

- `WorldGenRegion.setBlock(...)`
- `NoiseBasedChunkGenerator`
- carvers
- ore placement
- surface system
- below-zero retrogen
- flat generator spawn platform

Several are already covered by worldgen hooks, periodic sampling, or
`BulkSectionAccessMixin`. Keep this as an upgrade audit because direct writes
are exactly where canonical ownership can be bypassed.

Resolution plan:
[Worldgen direct mutation and structure persistence audit](worldgen-direct-mutation-and-structure-persistence-audit.md).

### Structure Query And Persistence

Structure generation, reference placement, and forced progression starts are
implemented, but saved alias starts and structure query/persistence behavior
remain an explicit open boundary in the worldgen docs.

Resolution plan:
[Worldgen direct mutation and structure persistence audit](worldgen-direct-mutation-and-structure-persistence-audit.md).

### World Border, Spawn Protection, And Respawn Metadata

`ServerLevel.mayInteract(...)` still asks vanilla spawn protection and world
border checks about the raw block position. Player lifecycle canonicalization
covers login, respawn, and bed wake-up, but commands such as `spawnpoint` and
`setworldspawn` store raw coordinates.

These are now explicit vanilla/admin policy boundaries. Ordinary player block
interaction tests the raw world border and the canonical spawn-protection owner
in tiled dimensions.

Implemented policy:
[Commands And Admin Coordinates](../mod-mechanics/commands.md).

### Long Rays And Occlusion

The shared topological ray helpers cover known item/projectile/AI paths, but
long rays default to a nearest-alias entity radius unless the caller opts into a
wider scan. Vibration occlusion is covered by game-event dispatch passing
listener-local source positions into vanilla vibration handling.

Reference:
[Game Events And Vibrations](../mod-mechanics/game-events-and-vibrations.md)
for vibration source frames, and
[Entity Query Caller Matrix](../mod-mechanics/entity-query-caller-matrix.md)
for caller-specific entity query policy.

### Client-Only Debug And Local Effects

Server packets are virtualized, but client-only debug subscribers, local
particles, and purely local ambience may still use raw client coordinates. Most
of this is presentation-only, but it can confuse testing if the debug overlay
or local effect appears to disagree with canonical state.

## Suggested Priority Order

1. Command/admin coordinate policy.
2. Entity collision policy and remaining narrow collision hooks.
3. Direct chunk mutation and structure persistence upgrade audit.
4. World border semantics for finite worlds.

## Regression Ideas

- Place a bed/job-site/hive/meeting point on one side of the tile and a villager,
  bee, raider, or trader trigger on the opposite visible side.
- Trigger a sculk sensor, shrieker, allay listener, and warden vibration across
  each X/Z edge and across a corner.
- Detonate TNT centered just inside a tile edge and compare entity damage,
  knockback, block destruction, and duplicate drops against an interior control.
- Test pressure plates, detector rails, hoppers, tripwire, conduits, beacons,
  shulker boxes, and piston pushes with entities only visible through an alias.
- Test default spawn lookup, bed respawn, respawn-anchor depletion, wandering
  trader placement, village sieges, and natural-spawn world-spawn exclusion near
  each tile edge.
- Use `fill`, `clone`, `place`, `locate`, `forceload`, `spawnpoint`, and
  `setworldspawn` near aliases and record whether raw behavior is acceptable or
  needs Globe-specific alternatives.
