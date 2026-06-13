# Entities

## What

Finite-world non-player entities are stored in canonical X/Z. Player-facing
packets render each entity in the nearest visible alias for that viewer.

Players may move through raw alias coordinates during normal play, but Globe
World rebases them to canonical X/Z at lifecycle boundaries such as login,
respawn, and bed wake-up.

## Why

Without canonical storage, mobs, items, vehicles, projectiles, and XP orbs can
duplicate outside the finite tile. Without viewer-relative packet positions, an
edge-near entity can appear far away or disappear for a player standing near
the opposite edge.

## Storage And Packets

`EntityCanonicalizer` continuously canonicalizes finite-world non-player
entities before add, after server ticks, and after same-dimension teleports.
Mounted non-player stacks are shifted together when the root vehicle wraps so
passengers preserve their offsets.

Player passengers stay in the visible virtual tile nearest their current server
position when a canonical vehicle positions riders. Client-controlled vehicle
movement arrives in the visible alias frame; `ServerGamePacketListenerImplMixin`
maps it back near vanilla's last accepted vehicle position before validation,
then canonicalizes the mounted stack after acceptance.

`EntityPacketUtil` virtualizes add, teleport, absolute sync, damage source,
vehicle correction, and minecart interpolation positions per viewer. Relative
movement, velocity, rotations, knockback vectors, and minecart step movement
stay relative. When an entity crosses the viewer-facing tile threshold, the
server sends an absolute sync and the client snaps tile-sized rebases instead
of interpolating across the tile. Standalone remote players use the same snap
path for visual rebases; the local player and mounted player stacks are skipped.
The entity packet helper uses `TopologyContext` for dimension-aware
viewer-nearest alias coordinates.

## Tracking, Ticking, And Spawning

Entity tracking uses wrapped X/Z distance and treats the virtual chunk nearest
the player as the tracked chunk. Alias chunks inside the player's tracking view
are eligible even while vanilla still marks the chunk packet pending, and
tracking refreshes immediately after chunk sends.

Canonical non-player entities tick once when their canonical chunk is
entity-ticking or when any visible alias of that canonical chunk is
entity-ticking. Alias chunks only satisfy the range gate; they do not create
duplicate entity ticks. Despawn checks use wrapped player distance.

Natural spawning stores candidates in canonical chunks, wraps candidate
positions and player-distance checks, counts mob caps by canonical chunk, and
dedupes spawning chunks by canonical key. Chunk-generation mob spawns are
cancelled for non-canonical chunks.

## AI, Interaction, And Pathing

`ActorLocalTargets` maps targets, hitboxes, and query boxes into the acting
mob's local tile frame. It can package those calculations into an
`ActorLocalTargetView` containing the canonical position, actor-local position,
actor-local hitbox, wrapped distances, same-level status, and aliasing status.
Broad query helpers route through `TopologicalEntityQueries`, which splits
visible-frame lookup boxes across canonical tile edges, dedupes canonical
entity identity, and adds alias-frame server players.
`TopologicalCollisionQueries` is the narrower collision adapter for audited
block triggers and placement/collision callers. It filters those deduped
candidates against the entity's nearest visible alias box instead of changing
global entity section storage.
Alias line of sight now delegates to the shared `TopologicalRaycasts` primitive,
which returns visible-frame block hits with canonical hit identity.
Targeting conditions, nearest-entity selection, brain sensors, target retention,
line of sight, look controls, melee checks, ranged-goal distance checks, and
move-toward-target goals use the nearest topological alias instead of raw
coordinates. Alias line of sight must be proven by a wrapped ray; wrapped
horizontal distance alone is not treated as visibility.

Ranged mob launch math uses the same target-alias convention before calculating
projectile X/Z vectors. Skeletons, illusioners, drowned, snow golems, llamas,
witches, crossbow mobs, blazes, ghasts, withers, and breezes aim at the nearest
target alias while preserving vanilla Y calculations, leading, potion choice,
charge timing, and inaccuracy. Creeper swelling, guardian beam attack gates,
and shulker attack range also use alias distance so a mob that has already
pathfound to a wrapped-near target can start and continue its attack.

Damage-source direction uses the same nearest-alias convention. Vanilla
`LivingEntity.hurtServer(...)` derives hurt knockback and damage indicators from
`DamageSource.getSourcePosition()`, and item blocking uses that position for
the incoming attack angle. `LivingEntityDamageSourceAliasMixin` maps entity
sources, such as zombies, to the victim's nearest alias before those checks run,
so a seam-adjacent melee hit pushes and blocks as if the attacker were in the
visible wrapped tile.

Projectile collision uses the shared `TopologicalRaycasts` primitives for the
server-authoritative move-vector path. `ProjectileUtilTopologicalMoveMixin`
routes vanilla's shared `ProjectileUtil.getHitResultOnMoveVector(...)` overloads
through `topologicalProjectileMove(...)`, covering thrown items, fishing
bobbers, llama spit, shulker bullets, fireworks, fireballs, and wind charges.
The result keeps visible-frame hit locations for movement while block callbacks
receive canonical block positions and entity hits point at the real entity.
The same mixin also routes server-side `ProjectileUtil` view-vector and
attack-range helpers through topological rays, so shared brush validation and
component-weapon sweeps use wrapped block/entity targets instead of raw space.

Arrows and tridents have separate vanilla arrow-family paths, so
`AbstractArrowAliasCollisionMixin` also wraps their direct block clip. It keeps
vanilla arrow entity hits, then uses `ProjectileAliasUtil` and the shared entity
sweep primitive to test candidate entities in the nearest alias frame to the
projectile's movement segment. Damage, pierce tracking, pickup, trident return,
and enchantment behavior stay on vanilla's entity identity while a skeleton
arrow or thrown trident can hit a player or mob through the visible wrapped
copy.

Splash-potion area effects use wrapped entity candidates and wrapped falloff
distance. `ThrownSplashPotionAliasEffectMixin` gathers candidates through the
shared topological entity query, then measures each candidate against the hit
potion AABB using that entity's nearest alias box. This lets witch splash
potions apply status effects to players and mobs visible in an alias tile while
preserving vanilla duration scaling and instant-effect math.

Audited block-trigger and narrow physical collision callers use visible-frame
entity boxes without duplicating entity identity. Pressure plates, weighted
pressure plates, arrow-activated buttons, detector rails, tripwire, hopper and
hopper-minecart item/container pickup, chest cat blocking, shulker lid
obstruction and lid pushing, item merge, old/new minecart push and pickup,
boat/minecart/armor-stand/end-crystal placement obstruction, and moving piston
displacement now query through the topological helpers. Resulting side effects
still mutate the one canonical entity. The maintained caller table is
in [Entity Query Caller Matrix](entity-query-caller-matrix.md).

Entity-derived path requests target the nearest alias block position. Small
tiles expand the request to nearby whole-tile target aliases so vanilla's
multi-target path search can choose a usable route. The pathfinder and node
evaluator themselves are still vanilla and not fully toroidal.

Player pickup and interaction reach checks use wrapped target boxes, so players
near a seam interact with the visible alias while packets still refer to the
canonical entity or block. Curved client picking is documented in
[Client](client.md).

Waypoint block, chunk, and azimuth packets use the receiver's nearest
`TopologyContext` alias. Waypoint range checks use wrapped distances in the
source dimension, and chunk visibility checks test the receiver-facing virtual
chunk.

Fishing bobbers remain canonical non-player entities, but owner-relative
fishing logic uses wrapped X/Z math. `FishingHookMixin` keeps vanilla's held-rod
and permission checks while replacing the owner distance gate with wrapped
distance, so an alias-frame player does not immediately discard a canonical
bobber. Retrieval pullback for caught loot and hooked entities also uses the
shortest wrapped X/Z delta toward the owner while preserving vanilla Y motion,
loot tables, durability, and open-water behavior.

## Visual Aliases

The client can draw extra presentation-only copies of non-player, not-leashed
entities at nearby whole-tile offsets. Standalone remote players also draw
presentation-only copies in the one-tile ring around the camera for small tile
worlds. These copies share the same real client entity id and are culled by
entity view distance, alias ring limit, frustum, and compiled-section
visibility. Alias-aware picking returns the canonical entity.

See [Client](client.md) for render toggles, snap-on-rebase behavior, and Iris
curvature interaction.

## Diagnostics

`/globeworld entity <target>` reports an entity's raw/canonical position,
canonicalization policy, root/passenger state, and the `ActorLocalTargetView`
for mob target alias/pathing distances when available.

`/globeworld entities` counts loaded entities that should be continuously
canonicalized but currently sit outside canonical X/Z.

## Key Files

- Core storage and packet virtualization:
  `EntityCanonicalizer`, `EntityPacketUtil`, `PlayerCanonicalizer`,
  `ServerLevelEntityMixin`, `ServerLevelEntityTickMixin`,
  `EntityTeleportCanonicalizationMixin`, `EntityPassengerPositionMixin`,
  `ServerEntityMixin`, `ChunkMapTrackedEntityMixin`,
  `ServerGamePacketListenerImplMixin`.
- Tracking, ticking, spawning, and despawn:
  `ChunkMapPlayerProviderMixin`, `ChunkMapSpawningMixin`,
  `NaturalSpawnerMixin`, `ChunkStatusTasksMixin`,
  `MobDespawnDistanceMixin`.
- AI and pathing:
  `ActorLocalTargetView`, `ActorLocalTargets`, `TopologicalEntityQueries`,
  `TopologicalRaycasts`,
  `MobNavigationAliasUtil`, `TargetingConditionsMixin`,
  `ServerEntityGetterMixin`, `NearestLivingEntitySensorMixin`, `SensingMixin`,
  `TargetGoalMixin`, `PathNavigationMixin`, `GroundPathNavigationMixin`,
  `FlyingPathNavigationMixin`, `LookControlMixin`, `MobLookMixin`,
  `MeleeAttackGoalMixin`, `RangedAttackGoalMixin`,
  `RangedBowAttackGoalMixin`, `RangedCrossbowAttackGoalMixin`,
  `LookAtPlayerGoalMixin`, `MoveTowardsTargetGoalMixin`,
  `AbstractSkeletonRangedAttackMixin`, `IllusionerRangedAttackMixin`,
  `DrownedRangedAttackMixin`, `SnowGolemRangedAttackMixin`,
  `LlamaRangedAttackMixin`, `WitchRangedAttackMixin`,
  `CrossbowItemRangedAttackMixin`, `BlazeAttackGoalMixin`,
  `GhastFacingMixin`, `GhastShootFireballGoalMixin`,
  `WitherBossRangedAttackMixin`, `BreezeShootMixin`,
  `SwellGoalMixin`, `GuardianAttackGoalMixin`,
  `GuardianAttackSelectorMixin`, `ShulkerAttackGoalMixin`.
- Damage direction:
  `LivingEntityDamageSourceAliasMixin`, `DamageAliasUtil`.
- Projectile collision:
  `AbstractArrowAliasCollisionMixin`, `ThrownSplashPotionAliasEffectMixin`,
  `ProjectileAliasUtil`, `ProjectileUtilTopologicalMoveMixin`,
  `TopologicalEntityQueries`, `TopologicalCollisionQueries`,
  `TopologicalRaycasts`.
- Block-trigger and narrow collision callers:
  `PressurePlateBlockEntityQueryMixin`,
  `WeightedPressurePlateBlockEntityQueryMixin`,
  `ButtonBlockEntityQueryMixin`, `DetectorRailBlockEntityQueryMixin`,
  `TripWireBlockEntityQueryMixin`, `HopperBlockEntityQueryMixin`,
  `MinecartHopperEntityQueryMixin`, `ChestBlockCatQueryMixin`,
  `ShulkerBoxBlockEntityQueryMixin`, `ShulkerBoxBlockEntityCollisionMixin`,
  `ItemEntityMergeMixin`,
  `OldMinecartBehaviorCollisionMixin`, `NewMinecartBehaviorCollisionMixin`,
  `BoatItemPlacementMixin`, `MinecartItemPlacementMixin`,
  `ArmorStandItemPlacementMixin`, `EndCrystalItemPlacementMixin`,
  `PistonMovingBlockEntityQueryMixin`.
- Player interaction and presentation:
  `PlayerInteractionRangeMixin`, `PlayerItemPickupMixin`,
  `FishingHookMixin`,
  `GlobeEntityAliasing`, `GlobeEntityAliasMode`, `GlobeVisualAliasUtil`,
  `LevelRendererMixin`, `ClientPacketListenerMixin`,
  `GlobeCurvedRaycast`, `WaypointPacketUtil`,
  `WaypointChunkConnectionMixin`, `WaypointBlockConnectionMixin`,
  `WaypointAzimuthConnectionMixin`.
- Local day/night entity hooks:
  `MonsterLocalDaylightMixin`, `PhantomSpawnerLocalDaylightMixin`,
  `MobLocalDaylightMixin`, `PatrolSpawnerLocalDaylightMixin`.
- Diagnostics:
  `GlobeDebugCommands`, `GlobeEntityAliasDiagnostics`.

## Related Vanilla Mechanics

- [Vanilla mobs and entities](../vanilla-mechanics/mobs-and-entities.md)
- [Vanilla chunk loading](../vanilla-mechanics/chunk-loading.md)

## Open Audits

- Stress-test canonical entity ticking from alias simulation chunks under heavy
  death/despawn cases.
- Raw vanilla `EntityGetter` replacement is intentionally not global. New
  collision and other side-effect-sensitive query paths need a row in
  [Entity Query Caller Matrix](entity-query-caller-matrix.md) before they use
  visible-frame boxes.
- Full toroidal pathfinding remains deferred; current path requests target
  useful aliases but vanilla node search does not wrap every neighbor relation.
- General projectile physics across tile seams remains separate from ranged mob
  target selection, launch vectors, facing, arrow entity-hit wrapping, and the
  fishing-specific owner/pullback fixes.
- Client projectile prediction still uses vanilla raw helpers. Server
  authority is topological, but seam-crossing projectiles need visual
  regression testing for correction snaps.
- Very long rays use the default nearest-alias entity radius unless a caller
  explicitly opts into a wider `EntitySweepOptions` radius. Keep long lines of
  sight and tiny-tile rays on the regression checklist.
- A `/globeworld raycast` diagnostic command would make future seam bug
  reports easier to inspect, but it is optional.
- Visual aliases currently skip leashed entities and mounted player stacks;
  player passenger/vehicle stacks need dedicated multiplayer testing.
