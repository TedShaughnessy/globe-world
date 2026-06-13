# Entity Query And Collision Resolution Plan

This plan resolves the block-trigger entity query and generic entity collision
gaps raised in the
[Minecraft coordinate coverage audit](minecraft-coordinate-coverage-audit.md).

## Problem

Globe World intentionally avoids a global replacement for
`Level.getEntities(...)` and `getEntitiesOfClass(...)`. Many vanilla callers use
entity query results for side effects, identity-sensitive collision, or storage
maintenance. A global topological replacement would create duplicate-action and
ordering risks.

The current approach is targeted hooks plus shared helpers. That is still the
right shape, but the remaining vanilla caller set needs a tracked matrix and a
first collision policy.

## Goals

- Classify vanilla entity query and collision call sites.
- Add hooks for block-trigger gameplay queries that should be topological.
- Decide whether physical entity-vs-entity collision across seams is a v1 goal.
- If yes, implement a narrow first wave of collision hooks.
- Keep canonical entity identity singular and side effects deduped.

## Non-Goals

- Global `EntityGetter` or entity section storage replacement.
- Full toroidal physics in one pass.
- Client-only render collision changes.

## Vanilla Source Anchors

- `net/minecraft/world/level/EntityGetter.java`
- `net/minecraft/server/level/ServerEntityGetter.java`
- `net/minecraft/world/level/entity/EntitySectionStorage.java`
- `net/minecraft/world/level/CollisionGetter.java`
- Blocks and block entities that call `getEntities` or
  `getEntitiesOfClass`, especially pressure plates, detector rails, tripwire,
  hoppers, conduits, beacons, shulker boxes, chests, beehives, and pistons.
- Vehicle placement, minecart, item pickup/merge, dismount, armor stand, and
  end crystal placement code.

## Current Globe Anchors

- `TopologicalEntityQueries`
- `ActorLocalTargets`
- `ServerEntityGetterMixin`
- `ContainerOpenersCounterMixin`
- `PlayerDetectorMixin`
- `PlayerItemPickupMixin`
- `ThrownSplashPotionAliasEffectMixin`
- `entities.md`
- `blocks-and-ticks.md`

## Caller Matrix

Create `docs/plans/entity-query-caller-matrix.md` or a vanilla mechanics page
before the broad hook pass. For every vanilla `getEntities` or
`getEntitiesOfClass` caller, classify it as one of:

- `Topological gameplay query`: visible-frame gameplay should cross seams.
- `Canonical storage query`: raw canonical storage is correct.
- `Client/render-only query`: leave alone unless presentation is broken.
- `Intentionally vanilla`: raw behavior is policy or too risky.
- `Already covered`: existing hook or helper covers it.

Each row should include:

- source file and method;
- query box origin;
- result side effects;
- duplicate-risk notes;
- proposed hook or explicit no-op decision;
- regression case.

## Concrete Implementation Plan

### 1. Create The Caller Matrix

Add `docs/plans/entity-query-caller-matrix.md`.

Populate it from common/server vanilla sources with these source groups:

- `net/minecraft/world/level/block/*`
- `net/minecraft/world/level/block/entity/*`
- `net/minecraft/world/entity/item/ItemEntity.java`
- `net/minecraft/world/entity/vehicle/**`
- `net/minecraft/world/item/BoatItem.java`
- `net/minecraft/world/item/MinecartItem.java`
- `net/minecraft/world/item/ArmorStandItem.java`
- `net/minecraft/world/item/EndCrystalItem.java`
- `net/minecraft/world/level/block/piston/PistonMovingBlockEntity.java`
- existing Globe mixins that already wrap entity queries.

Matrix columns:

| Source | Method | Query API | Classification | Side effects | Hook | Regression |
| --- | --- | --- | --- | --- | --- | --- |

Classifications:

- `Topological gameplay query`
- `Narrow physical collision`
- `Canonical storage query`
- `Client/render-only query`
- `Intentionally vanilla`
- `Already covered`

Risk mitigation:

- No implementation hook should be added until its row exists in the matrix.
- Any row with side effects must say how duplicate entities are deduped.
- Any row that mutates entity motion must say which visible alias frame supplies
  the direction/vector.

### 2. Extend Shared Query Helpers Instead Of Replacing Vanilla

Keep `TopologicalEntityQueries` as the broad lookup boundary. Add only small
utilities needed by block/collision hooks:

- `nearestAliasBox(Level level, BlockPos actorBlock, AABB targetBox)`
- `intersectsVisible(Level level, AABB visibleQueryBox, Entity entity)`
- `distanceSqrToVisibleBox(Level level, Vec3 visibleOrigin, Entity entity)`

If this would make `TopologicalEntityQueries` too broad, add
`TopologicalCollisionQueries` under `globe.world.topology` and keep it as a thin
adapter over `TopologicalEntityQueries`.

Do not patch `EntitySectionStorage`, `EntityGetter`, or
`Level.getEntities(...)` globally.

### 3. First-Wave Block Trigger Mixins

Add these mixins one at a time, with each registered in
`globe-world.mixins.json` only after it compiles and has a manual regression
case:

- `PressurePlateBlockEntityQueryMixin`: wrap the entity query in
  `PressurePlateBlock`.
- `WeightedPressurePlateBlockEntityQueryMixin`: same for
  `WeightedPressurePlateBlock`.
- `DetectorRailBlockEntityQueryMixin`: wrap minecart/entity detection in
  `DetectorRailBlock`.
- `TripWireBlockEntityQueryMixin`: wrap tripwire entity detection in
  `TripWireBlock`.
- `HopperBlockEntityQueryMixin`: wrap item/entity pickup queries in
  `HopperBlockEntity`.

Hook rules:

- Prefer `@WrapOperation` around the direct `Level.getEntities(...)` or
  `getEntitiesOfClass(...)` call.
- Pass the vanilla query box as the visible-frame box into
  `TopologicalEntityQueries`.
- Return real entity instances only once.
- Do not virtualize entity identity or mutate entity positions for trigger
  checks.

Acceptance criteria:

- A player, mob, item, or minecart visible across an X/Z seam triggers the block.
- Trigger strength/count remains stable when the query box overlaps both
  canonical and alias frames.

### 4. Second-Wave Block/Block-Entity Hooks

After the first wave is stable:

- `ChestBlockCatQueryMixin` or `ChestBlockEntityQueryMixin` for cat-blocked
  chest opening if uncovered by existing container-open hooks.
- `ShulkerBoxBlockEntityQueryMixin` for obstruction checks.
- `PistonMovingBlockEntityQueryMixin` for moving-block entity displacement.
- Any remaining beacon/conduit/container checks not already covered by
  `MobEffectUtilMixin`, `ContainerOpenersCounterMixin`, or
  `PlayerDetectorMixin`.

Piston risk mitigation:

- Treat piston movement as `Narrow physical collision`, not a generic trigger.
- Compute the piston movement vector in the block-local visible frame.
- Apply displacement to the real entity once.
- Add diagnostics before widening coverage if duplicate movement appears.

### 5. Narrow Physical Collision Policy

Adopt Policy B, but only for these first cases:

- item pickup already covered by `PlayerItemPickupMixin`;
- item merge in `ItemEntity`;
- minecart/entity pickup or push in minecart behavior classes;
- vehicle placement obstruction in `BoatItem` and `MinecartItem`;
- armor stand and end crystal placement obstruction checks;
- moving piston displacement.

Add mixins:

- `ItemEntityMergeMixin`
- `MinecartCollisionMixin` or separate mixins for
  `OldMinecartBehavior`/`NewMinecartBehavior` if their query paths differ.
- `BoatItemPlacementMixin`
- `MinecartItemPlacementMixin`
- `ArmorStandItemPlacementMixin`
- `EndCrystalItemPlacementMixin`

Collision hook rules:

- Use visible alias boxes for intersection/distance tests.
- Apply side effects to canonical real entities.
- Dedupe by identity before merge, pickup, push, placement rejection, or damage.
- Do not implement general mob/player pushing in this pass.

### 6. Diagnostics

Add `ENTITY_QUERIES` or reuse `ENTITIES` diagnostics if manual tests need it.

Log:

- source class/method label;
- visible query box;
- canonical split boxes;
- matched entity ids/types;
- selected alias box;
- duplicate count;
- side-effect count.

## Block-Triggered Query Priority

Prioritize systems where a block near a seam should react to an entity visible
through the alias:

1. Pressure plates and weighted pressure plates.
2. Detector rails and minecart checks.
3. Tripwire/entity inside shape checks.
4. Hopper item/entity pickup.
5. Shulker box obstruction and chest cat-blocking checks.
6. Piston moving-block entity displacement.
7. Any remaining beacon/conduit/container-style checks not already covered.

For each hook:

- use `TopologicalEntityQueries` to gather real entities;
- dedupe by entity identity;
- run distance/box checks against the block-local visible alias;
- pass real canonical entity identity to vanilla side effects.

## Collision Policy Decision

Before implementing generic physical collision hooks, choose one of these
policies and document it in `docs/mod-mechanics/entities.md`:

### Policy A: v1 Gameplay Queries Only

Seam-crossing detection triggers gameplay systems, but entity-vs-entity pushes,
vehicle collision, item merging, and dismount physics remain mostly vanilla
unless already covered.

This is simpler and avoids unexpected motion side effects, but users may see
entities overlap through a visual seam.

### Policy B: Narrow Physical Collision Hooks

Implement topological collision only for narrow, user-visible cases:

- item pickup and item merging;
- minecart pickup/push;
- vehicle placement obstruction checks;
- dismount search near seams;
- moving piston entity displacement.

This is more complete but must guard carefully against double push/damage/merge
side effects.

Recommended first choice: Policy B for item pickup/merge, detector rails,
vehicle placement, and moving piston displacement; defer general mob/player
pushes until these prove stable.

## Implementation Phases

### Phase 1: Matrix

Build the caller matrix from vanilla sources and current Globe hooks.

Acceptance criteria:

- Every vanilla common/server `getEntities` and `getEntitiesOfClass` caller has
  a classification.
- Existing hooks are marked so new work does not duplicate them.

### Phase 2: Block Trigger Hooks

Implement the high-priority topological gameplay query hooks one by one. Keep
each mixin local to the vanilla class when possible.

Acceptance criteria:

- Pressure plates, detector rails, tripwire, and hoppers work across X/Z/corner
  seams.
- Duplicate entity side effects are not observed when a query box spans both
  sides of a tile.

### Phase 3: Collision Hooks

Implement only the selected Policy B narrow hooks, or document Policy A if
collision is deferred.

Acceptance criteria for Policy B:

- Items merge or get picked up across a seam without duplication.
- Minecarts detect and interact with nearby entities across a seam.
- Vehicle placement detects entities visible through a seam.
- Moving pistons displace entities across a visible seam once.

### Phase 4: Diagnostics

If tests are hard to interpret, add a diagnostic command or log path that
prints:

- visible query box;
- canonical split boxes;
- matched entity ids;
- alias box used for distance/collision;
- dedupe count.

## Documentation Updates When Implemented

- Move the final policy and implemented hooks into
  `docs/mod-mechanics/entities.md` and `docs/mod-mechanics/blocks-and-ticks.md`.
- Keep the caller matrix as a living audit if useful, or retire it once all
  rows are classified and stable.
- Shrink the block-trigger and collision sections in
  `minecraft-coordinate-coverage-audit.md`.
