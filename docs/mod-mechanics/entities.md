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

Mobs and item entities are canonicalized before being added to `ServerLevel`.
Item entities also re-canonicalize after ticking so drops that drift across a
tile edge remain stored in the finite tile. Entity add, teleport, and absolute
position-sync packets are virtualized per viewer. Relative movement packets stay
relative where possible.

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

For alias chunks, entity tracking treats chunks inside the player's tracking
view as eligible even while vanilla still has the chunk packet marked pending.
This avoids a slow one-by-one trickle of add-entity packets as alias chunks
finish sending. After a chunk packet is sent, player entity tracking is also
refreshed immediately. Each real entity still has one client entity id, so tiny
tiles that show multiple aliases at once render the nearest visible copy; when
that nearest alias changes, the server sends an absolute position sync to rebase
the client entity.

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

Mob sensing and targeting have partial wrapped-distance support. Pathfinding is
still an MVP compromise because vanilla path nodes and goals are raw Euclidean
positions.

## Key Files

- `src/main/java/globe/world/util/EntityPacketUtil.java`
- `src/main/java/globe/world/util/EntityCanonicalizer.java`
- `src/main/java/globe/world/util/CoordUtil.java`
- `src/main/java/globe/world/util/PlayerCanonicalizer.java`
- `src/main/java/globe/world/mixin/ServerLevelEntityMixin.java`
- `src/main/java/globe/world/mixin/ItemEntityMixin.java`
- `src/main/java/globe/world/mixin/PlayerItemPickupMixin.java`
- `src/main/java/globe/world/mixin/PlayerListCanonicalPositionMixin.java`
- `src/main/java/globe/world/mixin/ServerPlayerCanonicalPositionMixin.java`
- `src/main/java/globe/world/mixin/ServerEntityMixin.java`
- `src/main/java/globe/world/mixin/ServerGamePacketListenerImplMixin.java`
- `src/main/java/globe/world/mixin/ChunkMapTrackedEntityMixin.java`
- `src/main/java/globe/world/mixin/ChunkMapPlayerProviderMixin.java`
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

## Related Vanilla Mechanics

- [Vanilla mobs and entities](../../vanilla-mechanics/mobs-and-entities.md)
- [Vanilla chunk loading](../../vanilla-mechanics/chunk-loading.md)

## Open Audits

- Verify `Mob.checkDespawn` uses wrapped distance everywhere it needs to.
- Prove entity ticking runs exactly once for canonical entities when only aliases
  are entity-ticking.
- Improve wrapped sensing, targeting, line of sight, and pathfinding across tile
  edges.
