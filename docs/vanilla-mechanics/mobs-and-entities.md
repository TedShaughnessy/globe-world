# Mobs And Entities

Entities use continuous world coordinates, chunk/section indexing for storage and ticking, player distance checks for spawning/tracking, and packet synchronization for client visibility.

## Key Source Files

Common sources jar:

- `net/minecraft/server/level/ServerLevel.java`
- `net/minecraft/server/level/ServerChunkCache.java`
- `net/minecraft/server/level/ChunkMap.java`
- `net/minecraft/server/level/ServerEntity.java`
- `net/minecraft/server/network/ServerGamePacketListenerImpl.java`
- `net/minecraft/world/entity/Entity.java`
- `net/minecraft/world/entity/Mob.java`
- `net/minecraft/world/entity/ai/targeting/TargetingConditions.java`
- `net/minecraft/world/entity/ai/sensing/Sensing.java`
- `net/minecraft/server/level/ServerEntityGetter.java`
- `net/minecraft/world/entity/ai/sensing/NearestLivingEntitySensor.java`
- `net/minecraft/world/entity/ai/goal/target/TargetGoal.java`
- `net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java`
- `net/minecraft/world/entity/ai/goal/RangedAttackGoal.java`
- `net/minecraft/world/entity/ai/goal/RangedBowAttackGoal.java`
- `net/minecraft/world/entity/ai/goal/RangedCrossbowAttackGoal.java`
- `net/minecraft/world/entity/ai/goal/LookAtPlayerGoal.java`
- `net/minecraft/world/entity/ai/goal/MoveTowardsTargetGoal.java`
- `net/minecraft/world/entity/ai/control/LookControl.java`
- `net/minecraft/world/entity/ai/navigation/PathNavigation.java`
- `net/minecraft/world/level/NaturalSpawner.java`
- `net/minecraft/world/level/LocalMobCapCalculator.java`
- `net/minecraft/world/level/entity/PersistentEntitySectionManager.java`
- `net/minecraft/world/level/entity/EntitySectionStorage.java`
- `net/minecraft/world/level/entity/EntityTickList.java`

## Entity Ticking

`ServerLevel` owns the entity manager and tick list.

Important anchors:

- `ServerLevel.java:202` `entityTickList`
- `ServerLevel.java:205` `entityManager`
- `ServerLevel.java:417` entity tick loop
- `ServerLevel.java:423` entity-ticking range check
- `ServerLevel.java:812` `tickNonPassenger`
- `ServerLevel.java:826` `tickPassenger`
- `Entity.java:2319` `positionRider`
- `ServerGamePacketListenerImpl.java:442` `handleMoveVehicle`
- `ServerLevel.java:948` `addFreshEntity`
- `ServerLevel.java:983` `addEntity`
- `ServerLevel.java:1830` `getEntities`
- `ServerLevel.java:1886` `areEntitiesLoaded`
- `ServerLevel.java:1894` `isPositionEntityTicking`
- `Player.java:439` `aiStep` pickup scan calls `Level.getEntities(player, pickupArea)`
- `ItemEntity.java:329` `playerTouch` transfers the stack into the player's inventory

Entity manager callbacks wire entities into chunk tracking:

- `ServerLevel.java:1993` `onCreated`
- `ServerLevel.java:2007` `onTickingStart`
- `ServerLevel.java:2015` `onTrackingStart`
- `ServerLevel.java:2050` `onTrackingEnd`
- `ServerLevel.java:2079` `onSectionChange`

## Natural Spawning

`ServerChunkCache.tickChunks(...)` computes spawn state and calls natural spawning for eligible chunks.

Important anchors:

- `ServerChunkCache.java:372` spawn state creation
- `ServerChunkCache.java:393` collect spawning chunks
- `ServerChunkCache.java:399` tick spawning chunk
- `ServerChunkCache.java:415` `tickSpawningChunk`

`NaturalSpawner` handles cap checks, player distance, biome/structure spawn lists, and per-position tests.

Important anchors:

- `NaturalSpawner.java:69` `createState`
- `NaturalSpawner.java:106` `getFilteredSpawningCategories`
- `NaturalSpawner.java:123` `spawnForChunk`
- `NaturalSpawner.java:156` `spawnCategoryForPosition`
- `NaturalSpawner.java:187` nearest player lookup
- `NaturalSpawner.java:231` `isRightDistanceToPlayerAndSpawnPoint`
- `NaturalSpawner.java:257` despawn-distance guard
- `NaturalSpawner.java:287` `isValidPositionForMob`
- `NaturalSpawner.java:343` `getRandomPosWithin`
- `NaturalSpawner.java:366` chunk-generation creature spawning
- `NaturalSpawner.java:532` global cap check
- `NaturalSpawner.java:537` local cap check

## Mob Sensing, Targeting, And Path Requests

Several vanilla AI layers make their own raw-distance decisions after an entity
has already been accepted as a candidate:

- `TargetingConditions.test(...)` applies follow-range and line-of-sight checks.
- `Sensing.hasLineOfSight(...)` caches each target id as seen or unseen for the
  current sensing tick.
- `ServerEntityGetter.getNearestEntity(...)` chooses the nearest eligible
  candidate with `LivingEntity.distanceToSqr(...)`.
- `NearestLivingEntitySensor.doTick(...)` queries an inflated raw AABB and sorts
  candidates by raw distance before writing brain memories.
- `TargetGoal.canContinueToUse()` keeps or drops the current target with raw
  distance and cached sight checks.
- `MeleeAttackGoal`, `RangedAttackGoal`, `RangedBowAttackGoal`, and
  `RangedCrossbowAttackGoal` mix raw distance, line-of-sight cache checks, look
  control, and
  `PathNavigation.moveTo(target, ...)`.
- `PathNavigation.createPath(Entity, int)` converts the entity to
  `target.blockPosition()` before the pathfinder searches raw nodes. Ground and
  flying navigation override that entity method and do the same conversion in
  the subclass; ground navigation also adjusts a block target to a surface
  position before delegating to the raw path search.
- `LookControl.setLookAt(Entity, ...)` and `Mob.lookAt(Entity, ...)` turn toward
  raw target X/Z.

For wrapped worlds, these anchors need a consistent "nearest alias" convention:
storage identity stays on the real entity, but distance, sight, look, attack
reach, and entity path targets should use the topological copy nearest the
acting mob.

## Player Tracking And Entity Packets

`ChunkMap` owns player chunk tracking and entity tracking.

Important anchors:

- `ChunkMap.java:145` `playerMap`
- `ChunkMap.java:146` `entityMap`
- `ChunkMap.java:929` collect spawn candidate chunks
- `ChunkMap.java:954` `anyPlayerCloseEnoughForSpawning`
- `ChunkMap.java:998` `playerIsCloseEnoughForSpawning`
- `ChunkMap.java:1028` `updatePlayerStatus`
- `ChunkMap.java:1056` `move`
- `ChunkMap.java:1092` `updateChunkTracking`
- `ChunkMap.java:1128` `addEntity`
- `ChunkMap.java:1154` `removeEntity`
- `ChunkMap.java:1170` entity tracking tick
- `ChunkMap.java:1203` `sendToTrackingPlayers`
- `ChunkMap.java:1219` `sendToTrackingPlayersAndSelf`
- `ChunkMap.java:1378` `TrackedEntity.updatePlayer`
- `ChunkMap.java:1380` player/entity delta
- `ChunkMap.java:1383` horizontal distance squared

## Audit Questions

- Does an entity's true position differ from the position a player should receive?
- Which distance checks should use wrapped/topological distance rather than Euclidean world distance?
- Are entity section storage keys in storage coordinates or visible coordinates?
- Are spawn caps counted once per physical chunk or once per visible chunk?
- Do pathfinding, sensors, and targeting use the same distance convention as tracking?

## Globe World Notes

Natural spawning has two separate concerns:

- Candidate selection and caps must use canonical chunks/positions so aliases do not create duplicate real mobs.
- Player eligibility and packet visibility must use wrapped/virtual coordinates so players near a seam still interact with nearby canonical mobs.

Project hooks for natural spawning:

- `src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:23` cancels chunk-generation mob spawns for non-canonical chunks.
- `src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:39` samples random spawn positions from the canonical chunk in `spawnCategoryForChunk(...)`.
- `src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:53` passes canonical chunk and wrapped start position into `spawnCategoryForPosition(...)`.
- `src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:71` wraps `spawnCategoryForPosition(...)`'s start position at method entry.
- `src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:81` wraps chunk positions used for local mob caps.
- `src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:92` and `:114` wrap random spawn candidate chunk/block coordinates.
- `src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:103` wraps counted mob chunk positions during spawn-state creation.
- `src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:150` uses wrapped player distance for spawn-point distance checks.
- `src/main/java/globe/world/mixin/ChunkMapSpawningMixin.java:30` clears per-pass canonical spawn chunk tracking at the start of `ChunkMap.collectSpawningChunks(...)`.
- `src/main/java/globe/world/mixin/ChunkMapSpawningMixin.java:35` wraps the `List.add(...)` call in `collectSpawningChunks(...)`, swaps alias chunks for canonical chunks, and dedupes by canonical chunk key before `ServerChunkCache.tickSpawningChunk(...)` runs.
- `src/main/java/globe/world/mixin/ChunkMapSpawningMixin.java:68` uses wrapped chunk distance for `playerIsCloseEnoughForSpawning(...)`.

Project hooks for entity storage and visibility:

- `src/main/java/globe/world/util/EntityCanonicalizer.java:11` defines the shared
  policy for continuously canonicalized finite-world non-player entities.
- `src/main/java/globe/world/util/EntityCanonicalizer.java:50` canonicalizes a
  root entity and shifts its mounted non-player passenger stack together.
- `src/main/java/globe/world/mixin/ServerLevelEntityMixin.java:18` canonicalizes
  non-player entities before `ServerLevel.addEntity(...)` stores them.
- `src/main/java/globe/world/mixin/ServerLevelEntityMixin.java:23` and `:28`
  canonicalize non-player entities loaded from chunk/entity streams.
- `src/main/java/globe/world/mixin/ServerLevelEntityTickMixin.java` lets a
  canonical entity satisfy `ServerLevel.tick(...)`'s entity-ticking range gate
  through the viewer-nearest entity-ticking alias chunk, then canonicalizes
  non-player entities after root/passenger server ticks.
- `src/main/java/globe/world/mixin/EntityTeleportCanonicalizationMixin.java:15`
  and `:27` canonicalize non-player entities after same-level teleport
  positioning.
- `src/main/java/globe/world/mixin/EntityPassengerPositionMixin.java:12`
  keeps player passengers in their visible virtual tile when canonical
  non-player vehicles position riders.
- `src/main/java/globe/world/mixin/MobDespawnDistanceMixin.java` wraps
  `Mob.checkDespawn()`'s player-to-mob distance so canonical mobs near a player
  alias are not treated as raw-distance far away.
- `src/main/java/globe/world/util/AiAliasUtil.java` computes mob-local target
  aliases, alias hitboxes, query boxes, and wrapped AI distances without moving
  or cloning entities.
- `src/main/java/globe/world/mixin/SensingMixin.java` makes
  `Sensing.hasLineOfSight(...)` return alias sight results itself so vanilla's
  seen/unseen cache matches wrapped targeting.
- `src/main/java/globe/world/mixin/TargetingConditionsMixin.java`,
  `ServerEntityGetterMixin.java`, and `NearestLivingEntitySensorMixin.java`
  apply wrapped distance to eligibility, nearest-candidate ordering, and brain
  memory ordering.
- `src/main/java/globe/world/mixin/TargetGoalMixin.java`,
  `LookAtPlayerGoalMixin.java`, `MoveTowardsTargetGoalMixin.java`,
  `MeleeAttackGoalMixin.java`, `RangedAttackGoalMixin.java`, and
  `RangedBowAttackGoalMixin.java` keep target retention, movement, and attack
  distance checks in the acting mob's alias frame. Crossbow mobs receive the
  same distance treatment through `RangedCrossbowAttackGoalMixin.java`.
- `src/main/java/globe/world/mixin/PathNavigationMixin.java`,
  `GroundPathNavigationMixin.java`, and `FlyingPathNavigationMixin.java`
  redirect entity-derived path requests to the nearest alias block position. If
  the tile is smaller than the mob's follow range, they instead offer a
  one-tile-radius set of nearby target alias block positions, preserving each
  vanilla navigation class's entity-path search settings while letting vanilla
  choose the best reachable alias.
- `src/main/java/globe/world/mixin/LookControlMixin.java` and
  `MobLookMixin.java` turn mobs toward nearest target aliases and use alias
  hitboxes for melee reach.
- `src/main/java/globe/world/util/MobNavigationAliasUtil.java` marks mobs after
  a canonicalization snap so melee goals immediately clear stale path target
  coordinates and recompute.
- `src/main/java/globe/world/mixin/ServerGamePacketListenerImplMixin.java:38`
  maps client vehicle movement packets from the visible alias frame to the
  nearest storage frame before vanilla movement validation, then canonicalizes
  the mounted stack after accepted vehicle moves.
- `src/main/java/globe/world/mixin/ChunkMapTrackedEntityMixin.java:57` maps an entity's canonical chunk to the viewer's nearest alias and allows alias tracking by tracking-view membership rather than vanilla's pending-chunk gate.
- `src/main/java/globe/world/mixin/ChunkMapTrackedEntityMixin.java:69` tracks each player's current virtual chunk for a visible entity and sends an absolute sync when the nearest alias changes.
- `src/main/java/globe/world/mixin/ChunkMapSpawningMixin.java:27` and `:46` translate canonical chunk lookup to each player's nearest tracked virtual chunk for player-provider queries.
- `src/main/java/globe/world/mixin/PlayerChunkSenderMixin.java:74` refreshes entity tracking after a chunk packet is sent, so entities missed while the chunk was pending pair immediately.
- `src/main/java/globe/world/util/ChunkAliasTracker.java:16` tracks loaded aliases per player and canonical chunk for block/entity packet fanout.
- `src/main/java/globe/world/GlobeDebugCommands.java:74` reports one entity's
  canonical storage status; `:111` summarizes loaded entities outside canonical
  X/Z in the command source's dimension.

Current status:

- Good: finite-world non-player entities are stored in canonical coordinates on
  add/load, after server ticks, and after same-level teleport positioning.
- Good: mounted non-player stacks are shifted together when the non-player root
  wraps, while player passengers keep the visible tile used for chunk streaming.
- Good: player-controlled vehicle movement packets are translated from visible
  alias coordinates before vanilla can store the vehicle in an alias section.
- Good: player pickup scans query the player's canonical pickup box as well as
  the raw box.
- Good: debug commands can report selected entity storage state and count loaded
  non-player entities outside canonical X/Z.
- Good: spawn position math and mob caps are mostly wrapped to canonical chunk identity.
- Good: `ServerChunkCache.tickSpawningChunk(...)` now receives each canonical chunk at most once per `collectSpawningChunks(...)` pass, avoiding duplicate spawn attempts, inhabited-time increments, and thunder work from aliases.
- Partial: mob AI now uses nearest-alias distance, sight cache, look, melee
  reach, and a small-tile multi-alias entity path target set. The underlying
  pathfinder/node evaluator is still raw rather than fully toroidal, and
  projectile physics across seams are not part of this AI pass.

Best rule of thumb:

Entity storage should be canonical. Player-facing entity packets and distance checks should choose the nearest virtual copy for each viewer. Spawn/chunk tick lanes should dedupe by canonical chunk before they run side effects.
