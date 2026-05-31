# Entities

## What

Mobs and entities that belong to the finite world are stored in canonical X/Z.
When a player views or tracks them, outbound packets place the entity in the
nearest virtual copy for that viewer.

## Why

Without canonical storage, a mob spawned in an alias can become a duplicate
entity outside the canonical tile. Without viewer-relative packet positions, an
edge-near mob can appear far away or disappear for a player standing near the
opposite edge.

## Implementation

Finite-world non-player entities are canonicalized before being added to
`ServerLevel`, after server entity ticks, and after same-dimension teleport
positioning. This includes mobs, item entities, vehicles, projectiles, XP orbs,
falling blocks, and other non-player entities in tiled dimensions. Mounted
non-player stacks are shifted together when the root vehicle wraps, so
passengers preserve their offsets from the vehicle. Player passengers stay in
the visible virtual tile nearest their current server position when canonical
vehicles position riders, which keeps player chunk streaming aligned with the
client while the vehicle remains canonical. Entity add, teleport, and absolute
position-sync packets are virtualized per viewer. Relative movement packets stay
relative where possible.

Player-controlled vehicle movement is received from the client in the visible
alias coordinate frame. Before vanilla validates a `ServerboundMoveVehiclePacket`,
Globe World maps the packet position into the storage frame nearest vanilla's
last accepted vehicle position. After vanilla accepts the move, the mounted
stack is canonicalized and the vehicle movement anchors are refreshed.

Players may travel through virtual coordinates during normal play. On login,
respawn, and bed wake-up, the server rebases the player to the canonical X/Z
equivalent before sending vanilla's position packet to the client. This keeps
long-running aliases from being persisted across those lifecycle boundaries
without changing in-session movement.

Entity tracking uses wrapped X/Z distance and checks the virtual chunk nearest
to the player. Natural spawning stores candidates in canonical chunks, wraps
candidate positions, wraps player distance checks, counts mob caps by canonical
chunk, and dedupes spawning chunks by canonical key. Chunk-generation mob spawns
are cancelled for non-canonical chunks. In scrolling day/night mode, hostile
spawn brightness checks use local sky darkening at the spawn position, phantom
spawning uses local sky darkening at the player position, and undead burning
uses local burn-time and brightness predicates at the mob position. Pillager
patrol attempts also use local daylight at the selected spawn position instead
of the dimension-wide bright-outside gate.

Canonical non-player entities tick when their canonical chunk is entity-ticking
or when any visible alias of that canonical chunk is entity-ticking. The tick
still runs once on the canonical entity; alias chunks only satisfy vanilla's
entity-ticking range gate. This prevents canonical mobs from becoming invisible
stale entities when a player is simulating an alias chunk, including dead mobs
that need `LivingEntity.tickDeath()` to finish removal.
Mob despawn checks use wrapped player distance, so canonical hostile mobs near
a virtual player alias are not instantly discarded by raw tile-offset distance.

For alias chunks, entity tracking treats chunks inside the player's tracking
view as eligible even while vanilla still has the chunk packet marked pending.
This avoids a slow one-by-one trickle of add-entity packets as alias chunks
finish sending. After a chunk packet is sent, player entity tracking is also
refreshed immediately. Each real entity still has one client entity id. The
client can draw presentation-only visual aliases of that one entity at whole
tile offsets when multiple terrain aliases are close enough to be visible.
The client limits those aliases to a configurable number of tile rings around
the camera while still respecting vanilla entity view distance. Alias-aware
client picking tests those visual alias boxes but returns the same canonical
entity id, so interaction and attack packets still target the real entity. When
the nearest network alias changes, the server sends an absolute position sync to
rebase the client entity. Rebase detection uses the same block-level tile offset
as packet virtualization, not just the entity's chunk alias, so it changes at
the same threshold as the viewer-facing position. Player-driven alias changes
sync immediately. Entity-driven alias changes replace the next relative
movement packet with an absolute position sync, avoiding a same-tick
absolute-sync plus relative-move double application on the client. The client
snaps tile-sized rebases instead of interpolating them, which prevents the real
client entity from visually sliding across the tile during a wrap correction.

Additional entity-adjacent packets with absolute positions are virtualized per
viewer. Damage event source positions, vehicle correction positions, and
minecart interpolation step positions are copied to the nearest visible alias.
Relative movement, velocity, rotations, minecart step movement, and knockback
vectors remain unchanged. Vehicle correction packets sent directly from
`ServerGamePacketListenerImpl.handleMoveVehicle(...)` use the same
`EntityPacketUtil` path as tracked-entity packets.

Player item pickup scans include the player's canonical pickup box as well as
the raw box. This lets a player standing in an alias collect the same canonical
item entity they see through virtualized packets.

Entity, attack, and block interaction range checks use wrapped target boxes so
players near a tile seam interact with the nearest visible copy instead of the
canonical copy's raw distance. The block-range path also protects vanilla flows
that revalidate block reach after an interaction begins, such as sign editing.

Entity storage diagnostics are available under `/globeworld debug`. Use
`/globeworld debug entity <target>` to inspect one entity's raw/canonical
position, canonicalization policy, and root/passenger state. Use
`/globeworld debug entities` to count loaded entities in the current dimension
that should be continuously canonicalized but are currently outside canonical
X/Z.

Mob sensing and targeting have partial wrapped-distance support. Pathfinding is
still an MVP compromise because vanilla path nodes and goals are raw Euclidean
positions.

## Key Files

- `src/main/java/globe/world/util/EntityPacketUtil.java`
- `src/main/java/globe/world/util/EntityCanonicalizer.java`
- `src/main/java/globe/world/util/CoordUtil.java`
- `src/main/java/globe/world/util/GlobeEntityAliasing.java`
- `src/main/java/globe/world/util/GlobeCurvedRaycast.java`
- `src/main/java/globe/world/util/PlayerCanonicalizer.java`
- `src/main/java/globe/world/GlobeDebugCommands.java`
- `src/main/java/globe/world/mixin/ServerLevelEntityMixin.java`
- `src/main/java/globe/world/mixin/ServerLevelEntityTickMixin.java`
- `src/main/java/globe/world/mixin/EntityPassengerPositionMixin.java`
- `src/main/java/globe/world/mixin/EntityTeleportCanonicalizationMixin.java`
- `src/main/java/globe/world/mixin/PlayerItemPickupMixin.java`
- `src/main/java/globe/world/mixin/PlayerListCanonicalPositionMixin.java`
- `src/main/java/globe/world/mixin/ServerPlayerCanonicalPositionMixin.java`
- `src/main/java/globe/world/mixin/ServerEntityMixin.java`
- `src/main/java/globe/world/mixin/ServerGamePacketListenerImplMixin.java`
- `src/main/java/globe/world/mixin/ChunkMapTrackedEntityMixin.java`
- `src/main/java/globe/world/mixin/ChunkMapPlayerProviderMixin.java`
- `src/main/java/globe/world/mixin/MobDespawnDistanceMixin.java`
- `src/main/java/globe/world/mixin/ChunkMapSpawningMixin.java`
- `src/main/java/globe/world/mixin/NaturalSpawnerMixin.java`
- `src/main/java/globe/world/mixin/MonsterLocalDaylightMixin.java`
- `src/main/java/globe/world/mixin/PhantomSpawnerLocalDaylightMixin.java`
- `src/main/java/globe/world/mixin/MobLocalDaylightMixin.java`
- `src/main/java/globe/world/mixin/PatrolSpawnerLocalDaylightMixin.java`
- `src/main/java/globe/world/mixin/ChunkStatusTasksMixin.java`
- `src/main/java/globe/world/mixin/NearestLivingEntitySensorMixin.java`
- `src/main/java/globe/world/mixin/TargetingConditionsMixin.java`
- `src/main/java/globe/world/mixin/PlayerInteractionRangeMixin.java`
- `src/client/java/globe/world/client/GlobeVisualAliasUtil.java`
- `src/client/java/globe/world/client/mixin/LevelRendererMixin.java`
- `src/client/java/globe/world/client/mixin/ClientPacketListenerMixin.java`

## Related Vanilla Mechanics

- [Vanilla mobs and entities](../../vanilla-mechanics/mobs-and-entities.md)
- [Vanilla chunk loading](../../vanilla-mechanics/chunk-loading.md)

## Open Audits

- Audit `Mob.checkDespawn` nearest-player selection in multiplayer alias
  layouts; despawn distance itself is wrapped.
- Manually validate canonical entity ticking from alias simulation chunks under
  heavy death/despawn cases.
- Improve wrapped sensing, targeting, line of sight, and pathfinding across tile
  edges.
- Visual entity aliases currently skip players, passengers, vehicles, and
  leashed entities until multi-entity render-state offsets are audited.
- Visual alias nameplates, shadows, and light sampling are first-pass behavior;
  verify them in tiny tiles before broadening entity-type support.
