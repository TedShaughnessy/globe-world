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
- `net/minecraft/world/entity/ExperienceOrb.java`
- `net/minecraft/world/entity/Mob.java`
- `net/minecraft/world/entity/ai/targeting/TargetingConditions.java`
- `net/minecraft/world/entity/ai/sensing/Sensing.java`
- `net/minecraft/server/level/ServerEntityGetter.java`
- `net/minecraft/world/entity/ai/sensing/NearestLivingEntitySensor.java`
- `net/minecraft/world/entity/ai/goal/target/TargetGoal.java`
- `net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java`
- `net/minecraft/world/entity/ai/goal/SwellGoal.java`
- `net/minecraft/world/entity/ai/goal/RangedAttackGoal.java`
- `net/minecraft/world/entity/ai/goal/RangedBowAttackGoal.java`
- `net/minecraft/world/entity/ai/goal/RangedCrossbowAttackGoal.java`
- `net/minecraft/world/entity/ai/goal/LookAtPlayerGoal.java`
- `net/minecraft/world/entity/ai/goal/MoveTowardsTargetGoal.java`
- `net/minecraft/world/entity/ai/goal/FollowOwnerGoal.java`
- `net/minecraft/world/entity/ai/goal/SitWhenOrderedToGoal.java`
- `net/minecraft/world/entity/ai/goal/LandOnOwnersShoulderGoal.java`
- `net/minecraft/world/entity/animal/feline/Cat.java`
  (`Cat.CatRelaxOnOwnerGoal`)
- `net/minecraft/world/entity/TamableAnimal.java`
- `net/minecraft/world/entity/ai/control/LookControl.java`
- `net/minecraft/world/entity/ai/navigation/PathNavigation.java`
- `net/minecraft/world/level/pathfinder/PathFinder.java`
- `net/minecraft/world/level/pathfinder/NodeEvaluator.java`
- `net/minecraft/world/level/pathfinder/Node.java`
- `net/minecraft/world/entity/monster/EnderMan.java`
- `net/minecraft/world/entity/monster/Creeper.java`
- `net/minecraft/world/entity/monster/skeleton/AbstractSkeleton.java`
- `net/minecraft/world/entity/monster/illager/Illusioner.java`
- `net/minecraft/world/entity/monster/zombie/Drowned.java`
- `net/minecraft/world/entity/animal/golem/SnowGolem.java`
- `net/minecraft/world/entity/animal/equine/Llama.java`
- `net/minecraft/world/entity/monster/Witch.java`
- `net/minecraft/world/item/CrossbowItem.java`
- `net/minecraft/world/entity/monster/Blaze.java`
- `net/minecraft/world/entity/monster/Phantom.java`
- `net/minecraft/world/entity/monster/Ghast.java`
- `net/minecraft/world/entity/monster/Guardian.java`
- `net/minecraft/world/entity/monster/Shulker.java`
- `net/minecraft/world/entity/boss/wither/WitherBoss.java`
- `net/minecraft/world/entity/monster/breeze/Shoot.java`
- `net/minecraft/world/entity/projectile/ProjectileUtil.java`
- `net/minecraft/world/entity/projectile/Projectile.java`
- `net/minecraft/world/entity/projectile/arrow/AbstractArrow.java`
- `net/minecraft/world/entity/projectile/throwableitemprojectile/AbstractThrownPotion.java`
- `net/minecraft/world/entity/projectile/throwableitemprojectile/ThrownSplashPotion.java`
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
- `Player.java:490` `touch(Entity)` dispatches pickup effects through
  `Entity.playerTouch(Player)`
- `ExperienceOrb.java:153` `followNearbyPlayer` keeps or drops a following
  player with raw `distanceToSqr`, then builds the attraction vector from raw
  player X/Z
- `ExperienceOrb.java:180` `scanForMerges` queries nearby XP orbs with
  `Level.getEntities(EntityTypeTest, AABB, Predicate)`
- `ExperienceOrb.java:202` `tryMergeToExisting` performs award-time XP orb
  merging before a new orb is spawned
- `ExperienceOrb.java:221` `playerTouch` grants XP and repair effects once the
  player pickup scan touches the orb
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
- `LocalMobCapCalculator.java:21` asks `ChunkMap.getPlayersCloseForSpawning(...)`
  for per-player local cap owners.
- `ChunkMap.java:982` prefilters local cap owner lookup with
  `DistanceManager.hasPlayersNearby(...)` before scanning players.

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
- Ranged mob attack implementations often compute final projectile X/Z vectors
  from raw target coordinates after a goal has already decided to attack.
  Examples include skeleton/illusioner arrows, drowned tridents, snow golem
  snowballs, llama spit, witch splash potions, crossbow target overrides, blaze
  fireballs, ghast fireballs, wither skulls, and breeze wind charges.
- Phantoms are not projectile mobs, but their attack strategy has the same
  coordinate hazard: target acquisition can accept an alias-near player while
  `PhantomAttackStrategyGoal` and `PhantomSweepAttackGoal` still store raw
  target block/position coordinates for the anchor and swoop.
- `SwellGoal` is separate from `MeleeAttackGoal`: it starts creeper swelling
  with raw `creeper.distanceToSqr(target) < 9.0`, then keeps or cancels swelling
  with raw 7-block distance and cached line-of-sight checks.
- Endermen use a bespoke stare path. `EnderMan.isBeingStaredBy(...)` delegates
  to `LivingEntity.isLookingAtMe(...)`, which builds a gaze vector from raw X/Z
  and then calls `target.hasLineOfSight(this, ...)`. The
  `EndermanLookForPlayerGoal` selector runs that stare check before target
  acquisition, keeps a pending target during the aggro delay, uses raw
  `distanceToSqr(...)` for close/far teleport decisions, and calls
  `teleportTowards(target)` when the target is far. `teleportTowards(...)`
  computes its approach direction from raw enderman-to-target X/Z. The
  `EndermanFreezeWhenLookedAt` goal separately uses raw distance to decide
  whether to freeze, then looks at the raw target position.
- `PathNavigation.createPath(Entity, int)` converts the entity to
  `target.blockPosition()` before the pathfinder searches raw nodes. Ground and
  flying navigation override that entity method and do the same conversion in
  the subclass; ground navigation also adjusts a block target to a surface
  position before delegating to the raw path search.
- `PathFinder.findPath(...)` collects target block positions into a map keyed
  by `NodeEvaluator.getTarget(...)`. `NodeEvaluator` caches raw nodes by
  `Node.createHash(...)`, so distinct target positions that collide in that
  hash can become the same `Target` key before vanilla's collector runs.
- `FollowOwnerGoal` uses raw `TamableAnimal.distanceToSqr(owner)` for the
  start and stop distances, then calls `TamableAnimal.shouldTryTeleportToOwner`
  or `PathNavigation.moveTo(owner, ...)` during ticks. `TamableAnimal` uses raw
  owner distance for the 12-block teleport threshold and samples teleport
  positions around `owner.blockPosition()`.
- `SitWhenOrderedToGoal.canUse()` uses raw
  `TamableAnimal.distanceToSqr(owner)` for the nearby attacked-owner sitting
  gate.
- `LandOnOwnersShoulderGoal.tick()` tests raw bounding-box overlap between a
  shoulder-riding pet and its owner before mounting.
- `Cat.CatRelaxOnOwnerGoal` uses raw owner distance, the raw sleeping-player
  `blockPosition()`, and a raw cat query around the bed-adjacent goal position
  before navigating and lying down near the owner.
- `LookControl.setLookAt(Entity, ...)` and `Mob.lookAt(Entity, ...)` turn toward
  raw target X/Z.

For wrapped worlds, these anchors need a consistent "nearest alias" convention:
storage identity stays on the real entity, but distance, sight, look, attack
reach, and entity path targets should use the topological copy nearest the
acting mob.

## Hurt Knockback

`LivingEntity.hurtServer(...)` applies base hurt knockback after damage is
accepted. Projectile damage asks the projectile for a horizontal knockback
direction, but ordinary entity damage falls back to
`DamageSource.getSourcePosition()`, which usually returns the direct entity's
raw position. Vanilla then passes `sourceX - victimX` and `sourceZ - victimZ`
to `LivingEntity.knockback(...)`; the knockback method subtracts the normalized
direction from the victim's velocity, pushing the victim away from that source.

Important anchors:

- `DamageSources.java:210` creates mob attack sources with the mob as the
  direct entity.
- `DamageSource.java:105` returns an explicit damage position or the direct
  entity's raw `position()`.
- `LivingEntity.java:1231` branches projectile knockback away from ordinary
  source-position knockback.
- `LivingEntity.java:1235` computes the raw source-to-victim X/Z direction.
- `LivingEntity.java:1239` applies base hurt knockback.
- `LivingEntity.java:1613` normalizes the supplied X/Z direction and pushes the
  victim away from it.
- `LivingEntity.java:1283` `applyItemBlocking(...)` uses the same source
  position to decide whether a held blocking item faces the incoming attack.

For wrapped worlds, ordinary melee damage needs the damage source position
projected into the victim's nearest alias frame before vanilla computes
knockback, damage indicators, or shield-facing checks.

## Projectile Collision

`AbstractArrow.tick()` handles arrow movement directly. For physics-enabled
arrows it clips blocks from the current raw position to `position() +
deltaMovement`, then `stepMoveAndHit(...)` calls `findHitEntities(...)` for
entity hits along the same raw segment.

Important anchors:

- `AbstractArrow.java:181` reads the block state at the arrow's raw block
  position.
- `AbstractArrow.java:258` uses `Level.clipIncludingBorder(...)` for the raw
  block ray.
- `AbstractArrow.java:281` collects entity hits before deciding whether the
  first result is an entity hit or the block hit.
- `AbstractArrow.java:488` delegates entity collection to
  `ProjectileUtil.getManyEntityHitResult(...)`.
- `ProjectileUtil.java:173` queries raw `Level.getEntities(...)` and clips raw
  entity bounding boxes.
- `ProjectileUtil.java:31` and `ProjectileUtil.java:49` are the shared
  move-vector helpers used by throwable item projectiles, fishing bobbers,
  llama spit, shulker bullets, fireworks, hurting projectiles, and wind
  charges.
- `ProjectileUtil.java:56` is the shared view-vector helper used by brush
  targeting.
- `ProjectileUtil.java:38` is the shared attack-range sweep helper used by
  attack-range component weapons.

For wrapped worlds, launch vectors can be correct while vanilla arrow
collisions still need a separate alias-box pass. The `EntityHitResult` can refer
to the real entity even when the tested hitbox is a virtual copy, because
vanilla damage and piercing state are stored on the real entity identity.
Shared `ProjectileUtil` ray helpers need the same block-first/entity-sweep
ordering as vanilla while replacing raw block positions with canonical block
owners and testing real entities through their nearest visible alias boxes.

## Splash Potion Effects

`AbstractThrownPotion.onHit(...)` dispatches splash potions to
`ThrownSplashPotion.onHitAsPotion(...)` after the projectile has impacted. The
splash path builds a small `potionAabb` at the hit location, inflates it by the
4-block splash range, queries living entities in that raw box, and then applies
effects only when `potionAabb.distanceToSqr(entityBox) < 16.0`.

Important anchors:

- `ThrowableProjectile.java:50` gets a move-vector hit result and moves the
  projectile to the impact location.
- `AbstractThrownPotion.java:80` calls `onHitAsPotion(...)` for potion stacks
  with effects.
- `ThrownSplashPotion.java:44` moves the potion bounding box to the hit
  location.
- `ThrownSplashPotion.java:46` queries raw living entities in the inflated
  splash box.
- `ThrownSplashPotion.java:53` measures raw box-to-box splash distance before
  calculating effect scale and duration.

For wrapped worlds, both the affected-entity query and the per-target distance
falloff need the target's nearest alias box relative to the potion impact box.

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

- `mod-fabric/src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:23` cancels chunk-generation mob spawns for non-canonical chunks.
- `mod-fabric/src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:39` samples random spawn positions from the canonical chunk in `spawnCategoryForChunk(...)`.
- `mod-fabric/src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:53` passes canonical chunk and wrapped start position into `spawnCategoryForPosition(...)`.
- `mod-fabric/src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:71` wraps `spawnCategoryForPosition(...)`'s start position at method entry.
- `mod-fabric/src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:81` wraps chunk positions used for local mob caps.
- `mod-fabric/src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:92` and `:114` wrap random spawn candidate chunk/block coordinates.
- `mod-fabric/src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:103` wraps counted mob chunk positions during spawn-state creation.
- `mod-fabric/src/main/java/globe/world/mixin/NaturalSpawnerMixin.java:150` uses wrapped player distance for spawn-point distance checks.
- `mod-fabric/src/main/java/globe/world/mixin/NaturalSpawnerMixin.java` also wraps the `isRightDistanceToPlayerAndSpawnPoint(...)` neighboring-chunk `canSpawnEntitiesInChunk(...)` gate through `GlobeNaturalSpawning`, so pack members can remain valid when their canonical neighbor is justified by a player-visible alias.
- `mod-fabric/src/main/java/globe/world/mixin/ChunkMapSpawningMixin.java:30` clears per-pass canonical spawn chunk tracking at the start of `ChunkMap.collectSpawningChunks(...)`.
- `mod-fabric/src/main/java/globe/world/mixin/ChunkMapSpawningMixin.java:35` wraps the `List.add(...)` call in `collectSpawningChunks(...)`, swaps alias chunks for canonical chunks, and dedupes by canonical chunk key before `ServerChunkCache.tickSpawningChunk(...)` runs.
- `mod-fabric/src/main/java/globe/world/mixin/ChunkMapSpawningMixin.java:68` uses wrapped chunk distance for `playerIsCloseEnoughForSpawning(...)`.
- `mod-fabric/src/main/java/globe/world/mixin/ChunkMapSpawningMixin.java` also opens `getPlayersCloseForSpawning(...)`'s raw `DistanceManager.hasPlayersNearby(...)` prefilter when the canonical chunk is near a player through wrapping; the existing wrapped player-distance check then builds the local mob-cap owner list.
- `mod-fabric/src/main/java/globe/world/util/GlobeNaturalSpawning.java` centralizes alias-aware `canSpawnEntitiesInChunk(...)` and wrapped spawning-player proximity checks.
- `mod-fabric/src/main/java/globe/world/mixin/ServerChunkCacheNaturalSpawningMixin.java` lets `ServerChunkCache.tickSpawningChunk(...)` pass its final `canSpawnEntitiesInChunk(...)` gate when the canonical chunk's viewer-nearest alias is entity-spawnable for a non-spectator player.

Project hooks for entity storage and visibility:

- `mod-fabric/src/main/java/globe/world/util/EntityCanonicalizer.java:11` defines the shared
  policy for continuously canonicalized finite-world non-player entities.
- `mod-fabric/src/main/java/globe/world/util/EntityCanonicalizer.java:50` canonicalizes a
  root entity and shifts its mounted non-player passenger stack together.
- `mod-fabric/src/main/java/globe/world/mixin/ServerLevelEntityMixin.java:18` canonicalizes
  non-player entities before `ServerLevel.addEntity(...)` stores them.
- `mod-fabric/src/main/java/globe/world/mixin/ServerLevelEntityMixin.java:23` and `:28`
  canonicalize non-player entities loaded from chunk/entity streams.
- `mod-fabric/src/main/java/globe/world/mixin/ServerLevelEntityTickMixin.java` lets a
  canonical entity satisfy `ServerLevel.tick(...)`'s entity-ticking range gate
  through the viewer-nearest entity-ticking alias chunk, then canonicalizes
  non-player entities after root/passenger server ticks.
- `mod-fabric/src/main/java/globe/world/mixin/EntityTeleportCanonicalizationMixin.java:15`
  and `:27` canonicalize non-player entities after same-level teleport
  positioning.
- `mod-fabric/src/main/java/globe/world/mixin/EntityPassengerPositionMixin.java:12`
  keeps player passengers in their visible virtual tile when canonical
  non-player vehicles position riders.
- `mod-fabric/src/main/java/globe/world/mixin/MobDespawnDistanceMixin.java` wraps
  `Mob.checkDespawn()`'s player-to-mob distance so canonical mobs near a player
  alias are not treated as raw-distance far away.
- `mod-fabric/src/main/java/globe/world/mixin/ExperienceOrbAliasMixin.java`
  wraps XP-orb follow retention, attraction vectors, and merge scans so orbs
  use the visible seam relation while storage remains canonical.
- `mod-fabric/src/main/java/globe/world/entity/ActorLocalTargets.java` computes
  mob-local target aliases, alias hitboxes, query boxes, and wrapped AI
  distances without moving or cloning entities.
- `mod-fabric/src/main/java/globe/world/mixin/SensingMixin.java` makes
  `Sensing.hasLineOfSight(...)` return alias sight results itself so vanilla's
  seen/unseen cache matches wrapped targeting.
- `mod-fabric/src/main/java/globe/world/mixin/TargetingConditionsMixin.java`,
  `ServerEntityGetterMixin.java`, and `NearestLivingEntitySensorMixin.java`
  apply wrapped distance to eligibility, nearest-candidate ordering, and brain
  memory ordering.
- `mod-fabric/src/main/java/globe/world/mixin/TargetGoalMixin.java`,
  `LookAtPlayerGoalMixin.java`, `MoveTowardsTargetGoalMixin.java`,
  `MeleeAttackGoalMixin.java`, `RangedAttackGoalMixin.java`, and
  `RangedBowAttackGoalMixin.java` keep target retention, movement, and attack
  distance checks in the acting mob's alias frame. Crossbow mobs receive the
  same distance treatment through `RangedCrossbowAttackGoalMixin.java`.
- `mod-fabric/src/main/java/globe/world/mixin/PathNavigationMixin.java`,
  `GroundPathNavigationMixin.java`, and `FlyingPathNavigationMixin.java`
  redirect entity-derived path requests to the nearest alias block position. If
  the tile is smaller than the mob's follow range, they instead offer a
  one-tile-radius set of nearby target alias block positions, preserving each
  vanilla navigation class's entity-path search settings while letting vanilla
  choose the best reachable alias. `MobNavigationAliasUtil.java` filters that
  set by vanilla node hash and keeps the actor-nearest target when aliases
  collide.
- `mod-fabric/src/main/java/globe/world/mixin/LookControlMixin.java` and
  `MobLookMixin.java` turn mobs toward nearest target aliases and use alias
  hitboxes for melee reach.
- `mod-fabric/src/main/java/globe/world/mixin/PhantomAttackStrategyGoalMixin.java`
  and `PhantomSweepAttackGoalMixin.java` move phantom attack anchors, swoop
  targets, and target-box hit checks into the phantom-local alias frame.
- `mod-fabric/src/main/java/globe/world/util/MobNavigationAliasUtil.java` marks
  mobs after a canonicalization snap so melee goals immediately clear stale
  path target coordinates and recompute. It also filters expanded alias path
  target sets before they enter vanilla's raw node cache.
- `mod-fabric/src/main/java/globe/world/mixin/ServerGamePacketListenerImplMixin.java:38`
  maps client vehicle movement packets from the visible alias frame to the
  nearest storage frame before vanilla movement validation, then canonicalizes
  the mounted stack after accepted vehicle moves.
- `mod-fabric/src/main/java/globe/world/mixin/ChunkMapTrackedEntityMixin.java:57` maps an entity's canonical chunk to the viewer's nearest alias and allows alias tracking by tracking-view membership rather than vanilla's pending-chunk gate.
- `mod-fabric/src/main/java/globe/world/mixin/ChunkMapTrackedEntityMixin.java:69` tracks each player's current virtual chunk for a visible entity and sends an absolute sync when the nearest alias changes.
- `mod-fabric/src/main/java/globe/world/mixin/ChunkMapSpawningMixin.java:27` and `:46` translate canonical chunk lookup to each player's nearest tracked virtual chunk for player-provider queries.
- `mod-fabric/src/main/java/globe/world/mixin/PlayerChunkSenderMixin.java:74` refreshes entity tracking after a chunk packet is sent, so entities missed while the chunk was pending pair immediately.
- `mod-fabric/src/main/java/globe/world/util/ChunkAliasTracker.java:16` tracks loaded aliases per player and canonical chunk for block/entity packet fanout.
- `mod-fabric/src/main/java/globe/world/GlobeDebugCommands.java:74` reports one entity's
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
- Good: XP orb pickup, attraction, and merging use the visible wrapped frame
  instead of raw X/Z across tile seams.
- Good: debug commands can report selected entity storage state and count loaded
  non-player entities outside canonical X/Z.
- Good: spawn position math and mob caps are mostly wrapped to canonical chunk identity.
- Good: local mob caps can now find alias-frame players for canonical chunks instead of being short-circuited by vanilla's raw `DistanceManager.hasPlayersNearby(...)` cache.
- Good: `ServerChunkCache.tickSpawningChunk(...)` now receives each canonical chunk at most once per `collectSpawningChunks(...)` pass, avoiding duplicate spawn attempts, inhabited-time increments, and thunder work from aliases.
- Good: both natural-spawn chunk eligibility gates accept a player-visible alias of the canonical chunk, so alias-tile players can drive hostile spawns instead of requiring the canonical chunk itself to satisfy vanilla's raw entity-spawnability check.
- Partial: mob AI now uses nearest-alias distance, sight cache, look, melee
  reach, ranged launch vectors for common ranged mobs, and a small-tile
  multi-alias entity path target set filtered for vanilla node-hash collisions.
  The underlying pathfinder/node evaluator is still raw rather than fully
  toroidal, and projectile physics across seams are not part of this AI pass.

Best rule of thumb:

Entity storage should be canonical. Player-facing entity packets and distance checks should choose the nearest virtual copy for each viewer. Spawn/chunk tick lanes should dedupe by canonical chunk before they run side effects.
