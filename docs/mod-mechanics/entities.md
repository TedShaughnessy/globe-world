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
of interpolating across the tile.

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

`AiAliasUtil` maps targets, hitboxes, and query boxes into the acting mob's
local tile frame. Targeting conditions, nearest-entity selection, brain sensors,
target retention, line of sight, look controls, melee checks, ranged-goal
distance checks, and move-toward-target goals use the nearest topological alias
instead of raw coordinates.

Entity-derived path requests target the nearest alias block position. Small
tiles expand the request to nearby whole-tile target aliases so vanilla's
multi-target path search can choose a usable route. The pathfinder and node
evaluator themselves are still vanilla and not fully toroidal.

Player pickup and interaction reach checks use wrapped target boxes, so players
near a seam interact with the visible alias while packets still refer to the
canonical entity or block. Curved client picking is documented in
[Client](client.md).

## Visual Aliases

The client can draw extra presentation-only copies of non-player, not-leashed
entities at nearby whole-tile offsets. These copies share the same real client
entity id and are culled by entity view distance, alias ring limit, frustum, and
compiled-section visibility. Alias-aware picking returns the canonical entity.

See [Client](client.md) for render toggles, snap-on-rebase behavior, and Iris
curvature interaction.

## Diagnostics

`/globeworld debug entity <target>` reports an entity's raw/canonical position,
canonicalization policy, root/passenger state, and mob target alias/pathing
distances when available.

`/globeworld debug entities` counts loaded entities that should be
continuously canonicalized but currently sit outside canonical X/Z.

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
  `AiAliasUtil`, `MobNavigationAliasUtil`, `TargetingConditionsMixin`,
  `ServerEntityGetterMixin`, `NearestLivingEntitySensorMixin`, `SensingMixin`,
  `TargetGoalMixin`, `PathNavigationMixin`, `GroundPathNavigationMixin`,
  `FlyingPathNavigationMixin`, `LookControlMixin`, `MobLookMixin`,
  `MeleeAttackGoalMixin`, `RangedAttackGoalMixin`,
  `RangedBowAttackGoalMixin`, `RangedCrossbowAttackGoalMixin`,
  `LookAtPlayerGoalMixin`, `MoveTowardsTargetGoalMixin`.
- Player interaction and presentation:
  `PlayerInteractionRangeMixin`, `PlayerItemPickupMixin`,
  `GlobeEntityAliasing`, `GlobeEntityAliasMode`, `GlobeVisualAliasUtil`,
  `LevelRendererMixin`, `ClientPacketListenerMixin`,
  `GlobeCurvedRaycast`.
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
- Full toroidal pathfinding remains deferred; current path requests target
  useful aliases but vanilla node search does not wrap every neighbor relation.
- Projectile physics across tile seams remain separate from ranged mob target
  selection and facing.
- Visual aliases currently skip players and leashed entities; player
  passenger/vehicle stacks need dedicated multiplayer testing.
