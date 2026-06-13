# Topological POI And Village Queries Plan

This plan resolves the POI, village, raid, and lightning-rod gaps raised in the
[Minecraft coordinate coverage audit](minecraft-coordinate-coverage-audit.md).

## Problem

Vanilla `PoiManager` stores POIs by raw section/chunk coordinates and performs
range scans, nearest sorting, and village-distance graph traversal in raw X/Z.
Globe World already canonicalizes many block writes, so POI state should usually
be registered at canonical block positions, but vanilla discovery from an alias
frame can still miss a visually-near POI across a tile edge.

Do not globally rewrite `PoiManager`. Callers have different expectations around
ordering, occupancy mutation, village graph state, and chunk loading. The safer
boundary is a small topological helper plus targeted caller hooks.

## Goals

- Make player-visible POI discovery behave continuous across X/Z seams.
- Preserve canonical POI identity, occupancy, and village graph state.
- Keep raw vanilla behavior for dimensions where tiling is disabled.
- Avoid duplicate results when a search radius spans more than one alias of the
  same canonical POI.
- Add regression coverage for beds, jobs, hives, raids, and lightning rods.

## Non-Goals

- Full replacement of all `PoiManager` methods.
- Persisting alias POIs or alias village sections.
- Making admin/debug command output topological in this plan.

## Vanilla Source Anchors

- `net/minecraft/world/entity/ai/village/poi/PoiManager.java`
- `net/minecraft/world/entity/ai/village/poi/PoiRecord.java`
- Villager brain and POI callers under
  `net/minecraft/world/entity/ai/behavior/`
- Bee hive/flower callers under `net/minecraft/world/entity/animal/Bee.java`
- Raid and patrol callers under `net/minecraft/world/entity/raid/`
- Lightning rod lookup in `net/minecraft/world/entity/LightningBolt.java`

## Current Globe Anchors

- `TopologyContext`
- `TopologyContexts`
- `TopologicalEntityQueries`
- `ActorLocalTargets`
- `LodestoneTrackerMixin`
- `LightningBoltMixin`
- `blocks-and-ticks.md`
- `entities.md`

## Proposed Design

Add a `TopologicalPoiQueries` helper under `globe.world.topology`.

The helper should expose explicit query shapes rather than mirror every vanilla
method:

- `recordsInRange(level, center, radius, typePredicate, statusPredicate)`
- `nearestRecord(level, center, radius, typePredicate, statusPredicate)`
- `positionsInRange(...)` for callers that only need `BlockPos`
- `sectionsToVillage(level, sectionPos)` only if a targeted caller proves that
  raw village-distance graph behavior is user-visible across seams.

The helper should:

- canonicalize the search center for storage access;
- split the visible search area into canonical chunk/section ranges, similar to
  `TopologicalEntityQueries.canonicalQueryBoxes(...)`;
- query vanilla `PoiManager` only in canonical storage coordinates;
- dedupe by canonical POI `BlockPos`;
- compute caller-facing distance and ordering against the nearest alias of each
  canonical POI relative to the original visible search center;
- return canonical POI positions to vanilla occupancy/mutation paths unless a
  caller specifically needs a visible-frame position for navigation or display.

## Concrete Implementation Plan

### 1. Add The Shared Helper

Create `mod-fabric/src/main/java/globe/world/topology/TopologicalPoiQueries.java`.

Public API:

- `Stream<PoiRecord> recordsInRange(ServerLevel level, Predicate<Holder<PoiType>> type, BlockPos center, int radius, PoiManager.Occupancy occupancy)`
- `Stream<PoiRecord> recordsInSquare(...)`
- `Stream<BlockPos> positionsInRange(...)`
- `Optional<BlockPos> findClosest(...)`
- `Optional<Pair<Holder<PoiType>, BlockPos>> findClosestWithType(...)`
- `Optional<BlockPos> take(...)`
- `Optional<BlockPos> getRandom(...)`
- `int sectionsToVillage(ServerLevel level, SectionPos sectionPos)` only after a
  targeted caller needs it.

Internal helper records:

- `PoiCandidate(PoiRecord record, BlockPos canonicalPos, BlockPos visiblePos, double wrappedDistanceSqr)`
- `PoiSectionQuery(ChunkPos canonicalChunk, int sectionY)`

Implementation details:

- Early-return to vanilla `PoiManager` when `TopologyContexts.forLevel(level)` is
  disabled.
- Convert the visible search square/range into canonical query boxes using the
  same split-box idea as `TopologicalEntityQueries.canonicalQueryBoxes(...)`.
  For POI queries, convert each canonical box into the chunk range needed by
  `PoiManager.getInChunk(...)`.
- Query `level.getPoiManager().getInChunk(...)` for canonical chunks only.
- Dedupe with a `LongOpenHashSet` keyed by `candidate.getPos().asLong()`.
- Filter square/range predicates using wrapped distance from the visible center
  to the canonical POI's nearest alias.
- Sort closest-first methods by `wrappedDistanceSqr`, not raw
  `BlockPos.distSqr(center)`.
- Return canonical `BlockPos` values to callers. If navigation needs an
  actor-local target, let existing actor/pathing helpers map the canonical
  target into the actor's visible frame.
- Implement `take(...)` by sorting/filtering topological candidates, then
  calling `PoiRecord.acquireTicket()` on the real canonical record exactly once.

Risk mitigations:

- Do not inject into `PoiManager` public methods globally; use the helper only
  from targeted vanilla caller mixins.
- Never create alias POI records, sections, or village graph keys.
- Use vanilla `PoiManager.Occupancy` and `PoiRecord` ticket APIs so bed/job-site
  reservation semantics stay vanilla.
- Cap work by the vanilla radius and canonical split boxes. If the radius is
  greater than or equal to one tile width, query each canonical chunk at most
  once.

### 2. Add Targeted Caller Mixins

Add mixins only for user-visible POI discovery. Register each in
`mod-fabric/src/main/resources/globe-world.mixins.json`.

First wave:

- `AcquirePoiMixin`: wrap `PoiManager.take(...)` so villager job-site and
  meeting-point acquisition uses `TopologicalPoiQueries.take(...)`.
- `NearestBedSensorPoiMixin`: wrap bed searches so sleeping/nearby-bed sensing
  uses wrapped nearest ordering while returned memory stays canonical.
- `SetClosestHomeAsWalkTargetPoiMixin`: wrap nearest-home lookup for villagers
  and related entities.
- `BeePoiSearchMixin`: wrap hive/flower POI searches in `Bee`.
- `LightningBoltPoiMixin`: wrap lightning-rod `findClosest(...)` lookup.

Second wave after first-wave regression:

- `CatSpawnerPoiMixin`
- `WanderingTraderSpawnerPoiMixin`
- `RaiderPoiMixin`
- `RaidsPoiMixin`
- `GolemRandomStrollInVillageGoalMixin`
- `MoveThroughVillageGoalMixin`
- `GoToClosestVillageMixin`
- `LocateHidingPlaceMixin`
- `ValidateNearbyPoiMixin`
- `PoiCompetitorScanMixin`
- `YieldJobSiteMixin`

Existing covered/special cases:

- Keep `LodestoneTrackerMixin` as its own narrow validation hook.
- Leave `PortalForcer` out of this plan unless Nether portal testing finds a
  POI-specific seam issue.
- Leave `LocateCommand` raw under the command policy plan.

Mixin strategy:

- Prefer `@WrapOperation` around the direct `PoiManager` call at the vanilla
  caller.
- If a vanilla behavior stores a returned `BlockPos` in `GlobalPos` brain
  memory, return the canonical `BlockPos`.
- If the same method also computes walking distance to the returned POI, wrap
  that distance call separately to use the POI's nearest actor-local alias.

### 3. Add Diagnostics Only If Needed

If testing is ambiguous, add a `POI` diagnostics channel and log:

- raw center;
- canonical center;
- queried canonical chunks;
- candidate count and dedupe count;
- selected canonical POI;
- selected visible alias;
- occupancy mode.

Keep diagnostics off by default and rate-limit logs like
`ClientActionDiagnostics`.

## Caller Phases

### Phase 1: Read-Only User-Visible Discovery

Start with callers where topological nearest ordering matters but POI state
mutation is still vanilla:

- villager bed discovery;
- villager job-site discovery;
- bee hive and flower search;
- lightning-rod target selection.

Acceptance criteria:

- A villager or bee near one edge can find a valid POI visible across the seam.
- A lightning bolt can select a rod visible across the seam.
- Results are deduped if the search covers both a canonical POI and an alias of
  the same POI.

### Phase 2: Occupancy And Memory Safety

Audit the exact occupancy mutation paths for villager POI acquisition/release.
If vanilla stores the returned `GlobalPos` directly in brain memory, keep the
stored position canonical and map to actor-local aliases only for distance,
look, and navigation decisions.

Acceptance criteria:

- Villagers do not reserve duplicate alias copies of one bed or job site.
- Existing `ActorLocalTargets` and path target helpers can move the actor toward
  the nearest visible alias while memory still identifies canonical ownership.

### Phase 3: Raids And Village Sections

Handle raid/village mechanics after Phase 1 proves the helper. Raid logic may
depend on `sectionsToVillage(...)` and other section-distance graph behavior
rather than simple POI records.

Options:

1. Keep the village graph canonical and wrap only the specific raid/meeting
   point searches.
2. Add a `TopologicalPoiQueries.sectionsToVillage(...)` wrapper that checks
   nearby canonical section aliases and returns the minimum wrapped graph
   distance without mutating the graph.

Acceptance criteria:

- Raid center refresh and raider POI targeting behave consistently when village
  POIs sit across a visible seam.
- No alias section keys are persisted into the village graph.

## Tests And Manual Checks

- Bed on one side of an X seam; villager on the opposite visible side.
- Job site across an X seam, Z seam, and corner seam.
- Bee, hive, and flower separated by a seam.
- Lightning rod across a seam from a lightning strike.
- Raid started near a seam with beds/meeting points across the visible edge.

## Documentation Updates When Implemented

- Move durable behavior into `docs/mod-mechanics/entities.md` if the hook is
  actor/AI-facing, or `docs/mod-mechanics/blocks-and-ticks.md` if it is
  block/POI-state-facing.
- Add vanilla POI source anchors to `docs/vanilla-mechanics/` if this plan
  requires a deeper POI reference page.
- Retire or shrink the POI section in
  `minecraft-coordinate-coverage-audit.md`.
