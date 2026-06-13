# POI And Villages

## What

POI and village discovery uses topological X/Z distance for user-visible
gameplay across tile seams while keeping POI storage canonical. Beds, job
sites, meeting points, hives, village POIs, raid centers, cats, wandering
traders, raider village movement, hiding-place lookup, and lightning rods can
be discovered through the nearest visible alias.

The canonical POI record remains the only mutable identity. Reservations,
releases, debug updates, brain memories, and saved raid/village state continue
to use canonical `BlockPos` values.

## Why

Vanilla `PoiManager` stores POIs by raw section/chunk coordinates. A POI near
one edge of the canonical tile can be visually close to an actor on the opposite
edge, but raw vanilla scans sort and filter by the long way around. Without a
topological query layer, villagers, bees, raids, traders, cats, and lightning
could miss visible-near POIs across a seam.

## Query Model

`TopologicalPoiQueries` is the shared adapter. It leaves `PoiManager` storage
untouched, splits the visible search square into canonical query boxes, scans
canonical POI chunks with vanilla `getInChunk(...)`, dedupes by canonical POI
position, and ranks candidates by wrapped distance to the original visible
center.

The helper returns canonical positions and real `PoiRecord` instances. The
`take(...)` helper invokes the canonical record's ticket acquisition exactly
once through `PoiRecordAccessor`, so one bed or job site cannot be reserved
through multiple aliases.

Village section distance keeps the vanilla POI graph canonical. The topological
wrapper samples nearby section aliases and returns the minimum vanilla graph
distance, without writing alias section keys.

## Caller Hooks

Targeted mixins route user-visible POI callers through the helper instead of
rewriting `PoiManager` globally:

- Villager job-site and meeting-point acquisition:
  `AcquirePoiMixin`.
- Baby bed sensing and nearest-home walking:
  `NearestBedSensorPoiMixin`, `SetClosestHomeAsWalkTargetPoiMixin`.
- Hiding place and village movement:
  `LocateHidingPlacePoiMixin`, `GoToClosestVillagePoiMixin`,
  `MoveThroughVillageGoalPoiMixin`.
- Bee hive search:
  `BeePoiSearchMixin`.
- Lightning rod lookup and village-section checks:
  `ServerLevelPoiMixin`.
- Raid and raider POI behavior:
  `RaidsPoiMixin`, `RaiderPoiMixin`.
- Cat and wandering trader POI gates:
  `CatSpawnerPoiMixin`, `WanderingTraderSpawnerPoiMixin`.

`PathNavigationMixin` and `GroundPathNavigationMixin` also expand canonical
block targets into actor-local aliases. That lets canonical POI memories remain
stable while vanilla path search can choose a visible wrapped target near the
actor.

## Boundaries

The helper does not create alias POI records, persist alias village sections, or
make admin/debug POI commands topological. Bee flower selection is not a POI
lookup in Minecraft 26.1.2; it remains a block scan and is covered by the
ordinary block/pathing topology boundaries.

## Key Files

- `TopologicalPoiQueries`
- `PoiRecordAccessor`
- `AcquirePoiMixin`
- `NearestBedSensorPoiMixin`
- `SetClosestHomeAsWalkTargetPoiMixin`
- `LocateHidingPlacePoiMixin`
- `GoToClosestVillagePoiMixin`
- `MoveThroughVillageGoalPoiMixin`
- `BeePoiSearchMixin`
- `ServerLevelPoiMixin`
- `RaidsPoiMixin`
- `RaiderPoiMixin`
- `CatSpawnerPoiMixin`
- `WanderingTraderSpawnerPoiMixin`
- `PathNavigationMixin`
- `GroundPathNavigationMixin`

## Vanilla Source Anchors

- `net/minecraft/world/entity/ai/village/poi/PoiManager.java`
- `net/minecraft/world/entity/ai/village/poi/PoiRecord.java`
- `net/minecraft/world/entity/ai/behavior/AcquirePoi.java`
- `net/minecraft/world/entity/ai/sensing/NearestBedSensor.java`
- `net/minecraft/world/entity/animal/bee/Bee.java`
- `net/minecraft/server/level/ServerLevel.java`
- `net/minecraft/world/entity/raid/Raids.java`
- `net/minecraft/world/entity/raid/Raider.java`
