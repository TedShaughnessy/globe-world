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

Mobs are canonicalized before being added to `ServerLevel`. Entity add,
teleport, and absolute position-sync packets are virtualized per viewer. Relative
movement packets stay relative where possible.

Entity tracking uses wrapped X/Z distance and checks the virtual chunk nearest
to the player. Natural spawning stores candidates in canonical chunks, wraps
candidate positions, wraps player distance checks, counts mob caps by canonical
chunk, and dedupes spawning chunks by canonical key. Chunk-generation mob spawns
are cancelled for non-canonical chunks.

Mob sensing and targeting have partial wrapped-distance support. Pathfinding is
still an MVP compromise because vanilla path nodes and goals are raw Euclidean
positions.

## Key Files

- `src/main/java/globe/world/util/EntityPacketUtil.java`
- `src/main/java/globe/world/util/CoordUtil.java`
- `src/main/java/globe/world/mixin/ServerLevelEntityMixin.java`
- `src/main/java/globe/world/mixin/ServerEntityMixin.java`
- `src/main/java/globe/world/mixin/ChunkMapTrackedEntityMixin.java`
- `src/main/java/globe/world/mixin/ChunkMapPlayerProviderMixin.java`
- `src/main/java/globe/world/mixin/ChunkMapSpawningMixin.java`
- `src/main/java/globe/world/mixin/NaturalSpawnerMixin.java`
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
