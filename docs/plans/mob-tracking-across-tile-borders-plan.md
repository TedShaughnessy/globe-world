# Mob Tracking Across Tile Borders

Status: implemented nearest-alias AI pass. Retained as historical design notes
for the remaining deferred work: full toroidal pathfinding, server-side player
canonicalization, smooth path-node rebasing, complete non-player candidate
expansion, and projectile physics across tile seams.

## Goal

Mobs should keep sensing, targeting, looking at, navigating toward, and attacking
nearby entities through X/Z tile borders. The server should still store one
canonical mob and one real target entity. AI code should reason about the
nearest topological copy of that target when it computes distance, sight, look
direction, and path destinations.

This plan is about mob AI tracking, not client entity packet tracking. Network
entity tracking already has a first pass in `ChunkMapTrackedEntityMixin` and
`EntityPacketUtil`.

## Chosen Approach

Implement a narrow AI-frame pass:

- Keep entity storage, identity, saving, and damage targets canonical/real.
- Add a shared utility that maps a target position to the alias nearest to the
  acting mob.
- Use wrapped distance and cache-safe wrapped line of sight for target
  eligibility and continuation checks.
- Rewrite vanilla entity path/look call sites so mobs path and face toward the
  nearest alias of the same target.
- When a non-player mob canonicalizes at a tile edge, force navigation to
  recompute instead of trying to preserve stale path nodes.

Do not implement a full toroidal `PathFinder` yet, and do not canonicalize
players server-side as part of this work.

## Current Symptoms

- A mob near a canonical tile edge can acquire a player through wrapped
  distance checks, but loses practical pursuit once either the mob or player is
  represented on the other side of the raw coordinate seam.
- A mob can remember the target briefly, then fail follow-up goal checks that
  still use raw `Entity.distanceToSqr(...)` or raw line-of-sight cache results.
- Pathfinding to a target can aim for the target's raw block position instead
  of the nearest wrapped alias, so the path either fails or points the long way
  around the tile.
- After a non-player entity canonicalizes at the border, any existing path nodes
  and cached target coordinates may still be in the previous raw tile frame.

## Vanilla Anchors

Common sources jar:

- `net/minecraft/world/entity/ai/targeting/TargetingConditions.java`
- `net/minecraft/world/entity/ai/sensing/Sensing.java`
- `net/minecraft/server/level/ServerEntityGetter.java`
- `net/minecraft/world/entity/ai/sensing/NearestLivingEntitySensor.java`
- `net/minecraft/world/entity/ai/goal/target/TargetGoal.java`
- `net/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal.java`
- `net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java`
- `net/minecraft/world/entity/ai/goal/RangedAttackGoal.java`
- `net/minecraft/world/entity/ai/goal/RangedBowAttackGoal.java`
- `net/minecraft/world/entity/ai/goal/LookAtPlayerGoal.java`
- `net/minecraft/world/entity/ai/goal/MoveTowardsTargetGoal.java`
- `net/minecraft/world/entity/ai/control/LookControl.java`
- `net/minecraft/world/entity/Mob.java`
- `net/minecraft/world/entity/ai/navigation/PathNavigation.java`
- `net/minecraft/world/level/PathNavigationRegion.java`
- `net/minecraft/world/level/pathfinder/PathFinder.java`

Key behavior:

- `TargetingConditions.test(...)` performs range and line-of-sight checks.
- `ServerEntityGetter.getNearestEntity(...)` chooses nearest candidates with
  raw distance after eligibility passes.
- `NearestLivingEntitySensor.doTick(...)` queries a raw inflated AABB, sorts by
  raw distance, and writes brain memories.
- `TargetGoal.canContinueToUse()` drops targets with raw distance checks.
- `MeleeAttackGoal`, `RangedAttackGoal`, and `RangedBowAttackGoal` mix raw
  distance, raw line-of-sight cache checks, raw look direction, and raw
  `PathNavigation.moveTo(target, ...)`.
- `PathNavigation.createPath(Entity, int)` converts the target entity to
  `target.blockPosition()`, then the pathfinder works in that raw coordinate
  frame.

## Existing Globe World Hooks

- `TargetingConditionsMixin` wraps the range check and has a first-pass
  line-of-sight fallback when wrapped X/Z distance is shorter than raw distance.
- `NearestLivingEntitySensorMixin` adds wrapped-nearby players to brain sensor
  candidate lists when the raw inflated AABB misses them.
- `ServerLevelEntityTickMixin` lets a canonical entity tick if the nearest
  visible alias chunk is entity-ticking, then canonicalizes non-player roots
  after their tick.
- `MobDespawnDistanceMixin` wraps despawn distance.
- `ChunkMapTrackedEntityMixin` wraps network visibility and packet aliases for
  player clients.

These hooks are useful but incomplete. The implementation below should replace
the fragile parts, especially the line-of-sight fallback that can leave
vanilla's `Sensing` unseen cache in the wrong state.

## Phase 1: Add Shared AI Alias Utilities

Add `mod-fabric/src/main/java/globe/world/util/AiAliasUtil.java`.

Responsibilities:

- Return `false`/raw values when tiling is disabled or the entities are not in
  the same level.
- Compute the target position nearest to the actor:

```text
nearestAliasPosition(actor, target)
nearestAliasPosition(level, actorX, actorY, actorZ, targetX, targetY, targetZ)
nearestAliasBlockPos(actor, target)
```

- Compute wrapped distances:

```text
distanceToSqr(actor, target)
distanceToSqr(actor, targetX, targetY, targetZ)
horizontalDistanceToSqr(actor, target)
```

- Build alias-aware query boxes:

```text
canonicalQueryBox(level, rawBox)
nearestAliasQueryBox(actor, rawBox)
```

Implementation notes:

- Use `CoordUtil.virtualBlock(level, canonicalCoord, actorCoord)` for nearest
  alias coordinates.
- Keep Y unchanged.
- Do not move or clone entities. This utility only computes coordinates.
- Prefer clear method names over generic "wrap" names, because call sites need
  to distinguish canonical storage from mob-local alias targeting.

Acceptance:

- Non-tiled worlds return raw positions and raw distances.
- In a tiled world, an actor near one edge sees a target near the opposite edge
  at a small wrapped distance and receives an alias block position in the
  actor's local tile frame.

## Phase 2: Make Sensing Cache-Safe

Problem: `TargetingConditionsMixin` can call vanilla
`Sensing.hasLineOfSight(...)`, receive `false`, then return `true` by fallback.
Vanilla may still cache the target id as unseen, causing later direct
`mob.getSensing().hasLineOfSight(target)` calls in goals to fail during the
same sensing tick.

Implementation:

- Add `SensingMixin`.
- Wrap or inject in `Sensing.hasLineOfSight(Entity target)` around the vanilla
  `Mob.hasLineOfSight(...)` call.
- If vanilla line of sight is true, keep vanilla behavior.
- If vanilla line of sight is false and the target is a living/entity target in
  the same tiled server level:
  - Compute the target's nearest alias eye/body position relative to the sensing
    mob.
  - Perform a real ray check from the mob's eye to that alias position if there
    is a stable vanilla API available at this layer.
  - If a true alias ray is too invasive for the first patch, use the existing
    conservative fallback only when wrapped X/Z distance is shorter than raw
    distance, but make `Sensing.hasLineOfSight(...)` itself return true so the
    `seen` cache is populated correctly.
- Remove the line-of-sight fallback from `TargetingConditionsMixin` after
  `SensingMixin` owns this behavior. Keep the wrapped range check there.

Files:

- Add `mod-fabric/src/main/java/globe/world/mixin/SensingMixin.java`.
- Update `mod-fabric/src/main/java/globe/world/mixin/TargetingConditionsMixin.java`.
- Add `SensingMixin` to `mod-fabric/src/main/resources/globe-world.mixins.json`.

Acceptance:

- A mob that accepts a target through wrapped sight does not immediately lose it
  because of `Sensing`'s unseen cache.
- Existing raw line-of-sight behavior is unchanged away from tile borders.

## Phase 3: Stabilize Target Choice And Retention

Implementation:

- Add `ServerEntityGetterMixin`.
  - Wrap distance calls inside `getNearestEntity(...)` overloads so nearest
    selection uses `AiAliasUtil.distanceToSqr(...)` when a source exists.
  - For overloads where `source` is null, leave vanilla raw ordering.
- Update `NearestLivingEntitySensorMixin`.
  - Keep the current wrapped-nearby player inclusion.
  - Sort sensor candidates with wrapped distance from `body`, not raw
    `body::distanceToSqr`.
  - Consider broadening candidate inclusion to non-player living entities by
    querying a canonical/nearest-alias box and deduping by entity id. This can
    be deferred if the first target is hostile mobs versus players.
- Add targeted goal mixins for raw continuation distance:
  - `TargetGoalMixin`: wrap `mob.distanceToSqr(target)` in
    `canContinueToUse()`.
  - `LookAtPlayerGoalMixin`: wrap `mob.distanceToSqr(lookAt)` in
    `canContinueToUse()`.
  - `MoveTowardsTargetGoalMixin`: wrap `target.distanceToSqr(mob)` checks in
    `canUse()` and `canContinueToUse()`.
  - `RangedAttackGoalMixin`: wrap `mob.distanceToSqr(targetX, targetY,
    targetZ)`.
  - `RangedBowAttackGoalMixin`: wrap `mob.distanceToSqr(targetX, targetY,
    targetZ)`.

Files:

- Add `mod-fabric/src/main/java/globe/world/mixin/ServerEntityGetterMixin.java`.
- Add or update the goal mixins listed above.
- Update `mod-fabric/src/main/resources/globe-world.mixins.json`.

Acceptance:

- A zombie near the west edge and a player just across the east/west seam keep
  target lock for more than one goal cycle.
- Multiple players on different aliases are ordered by wrapped distance from
  the mob.
- Brain sensor memories keep wrapped-nearest ordering.

## Phase 4: Path To The Nearest Target Alias

Implementation:

- Add `PathNavigationMixin`.
- Redirect or wrap `PathNavigation.createPath(Entity target, int reachRange)`.
  - Replace `target.blockPosition()` with
    `AiAliasUtil.nearestAliasBlockPos(mob, target)`.
  - Delegate to the existing block-position path creation path.
- Redirect or wrap `PathNavigation.moveTo(Entity target, double speed)`.
  - Build a path to the nearest alias block position.
  - Move along that path with the original speed.
- Add `TargetGoalMixin` coverage for `canReach(...)`.
  - The final-node comparison should compare against the same nearest alias
    target block, not the raw target block.
- Add `MeleeAttackGoalMixin`.
  - Store `pathedTargetX/Y/Z` as the nearest alias position, not raw target
    coordinates.
  - Use wrapped distance for recompute delay thresholds.
  - Ensure `moveTo(target, speed)` goes through the patched `PathNavigation`
    entity path.

Files:

- Add `mod-fabric/src/main/java/globe/world/mixin/PathNavigationMixin.java`.
- Extend `mod-fabric/src/main/java/globe/world/mixin/TargetGoalMixin.java`.
- Add `mod-fabric/src/main/java/globe/world/mixin/MeleeAttackGoalMixin.java`.
- Update `mod-fabric/src/main/resources/globe-world.mixins.json`.

Acceptance:

- Zombie at one tile edge paths through the seam toward a player just across the
  opposite edge.
- The path target reported by debug output is near the mob, not a tile width
  away.
- A normal non-border chase behaves like vanilla.

## Phase 5: Recompute Navigation After Canonicalization

Implementation:

- Add a small utility, probably `MobNavigationAliasUtil`.
- When `EntityCanonicalizer.canonicalizeRootStack(...)` moves a non-player root
  by a tile delta:
  - If the root is a `Mob`, call a helper to stop or force-recompute its
    navigation.
  - Prefer clearing/recomputing the path over shifting private `Path` nodes.
  - Reset stuck/timeout state if practical through accessors; if not, stopping
    the path is the safe first version.
- If vanilla goal recompute cooldown causes a visible pause, add a focused hook
  to let `MeleeAttackGoal` recompute on the next tick after a wrap.

Files:

- Update `mod-fabric/src/main/java/globe/world/util/EntityCanonicalizer.java`.
- Add `mod-fabric/src/main/java/globe/world/util/MobNavigationAliasUtil.java` if the logic
  is more than a couple of lines.
- Add accessors only if stopping navigation is not enough.

Acceptance:

- A mob already chasing a player can cross the tile border, canonicalize, and
  resume pursuit instead of standing still or pathing to the old raw tile.
- The canonicalization snap is not treated as a stuck-path failure.

## Phase 6: Look And Attack Polish

Implementation:

- Add `LookControlMixin`.
  - Wrap `setLookAt(Entity target, ...)` and `setLookAt(x, y, z, ...)` where
    the source is entity-derived, so the wanted X/Z use the nearest target
    alias.
- Add or extend a `MobLookMixin`.
  - Wrap `Mob.lookAt(Entity, ...)` to use nearest alias X/Z.
- Wrap melee reach if needed.
  - Audit `Mob.isWithinMeleeAttackRange(...)` and any direct callers used by
    `MeleeAttackGoal`.
  - Use wrapped distance to the target's nearest alias.
- For ranged mobs:
  - Distance-derived attack power should use wrapped distance.
  - Projectile spawn and aim may need a later packet/physics pass if arrows
    still travel the long raw direction. The first implementation should at
    least make mobs choose, face, and begin attacks using wrapped distance.

Files:

- Add `mod-fabric/src/main/java/globe/world/mixin/LookControlMixin.java`.
- Add `mod-fabric/src/main/java/globe/world/mixin/MobLookMixin.java` if needed.
- Extend ranged goal mixins from Phase 3.
- Update `mod-fabric/src/main/resources/globe-world.mixins.json`.

Acceptance:

- Skeleton near a seam faces the nearest player alias rather than rotating
  toward the long raw direction.
- Melee mobs attack when physically adjacent through the seam.
- Ranged mobs do not stop pursuing because raw distance says the target is far
  away.

## Phase 7: Diagnostics

Add debug output for a selected entity under the existing `/globeworld debug`
surface.

Useful fields:

- Raw mob position.
- Raw target position and target entity id.
- Nearest alias target position.
- Wrapped distance squared.
- Raw distance squared.
- Current navigation target position.
- Whether the last canonicalization forced navigation recompute.
- Last line-of-sight result and whether it came from raw or alias sight.

Files:

- Update `mod-fabric/src/main/java/globe/world/GlobeDebugCommands.java`.
- Add helper state only where needed; avoid storing per-entity diagnostics on
  all mobs unless it is cheap.

Acceptance:

- While reproducing a seam chase, the debug command explains which alias the mob
  is targeting and whether navigation was reset after wrapping.

## Implementation Order

1. Add `AiAliasUtil`.
2. Make line of sight cache-safe in `SensingMixin`.
3. Patch target choice and retention distances.
4. Patch `PathNavigation` entity targets and `TargetGoal.canReach()`.
5. Patch `MeleeAttackGoal` caches and recompute distances.
6. Force navigation recompute after canonicalization.
7. Patch look and ranged polish.
8. Add diagnostics.
9. Update durable docs in `docs/mod-mechanics/entities.md` and
   `docs/vanilla-mechanics/mobs-and-entities.md` after behavior is implemented.

## Validation Checklist

- Zombie starts on the west edge, player stands just across the east/west seam:
  zombie acquires, keeps, paths to, and attacks the player.
- Repeat with the zombie crossing the seam while already targeting the player.
- Repeat with the player crossing the seam while already targeted.
- Skeleton near a seam faces and shoots toward the nearest player alias, not
  across the long raw distance.
- Multiple players near different aliases of the same canonical location: mob
  chooses the nearest wrapped player consistently.
- Brain-based mobs still populate nearest-visible memories in wrapped order.
- Non-player targets across the seam are either supported or explicitly listed
  as follow-up scope after player-targeting hostile mobs work.
- Non-tiled dimensions behave exactly like vanilla.
- Large tiles do not introduce measurable AI overhead in ordinary play.

## Deferred Work

- Full toroidal `PathFinder`/`NodeEvaluator` support.
- Server-side canonical player storage.
- Smooth path node rebasing instead of navigation recompute.
- Complete non-player target candidate expansion for every goal/sensor.
- Projectile physics that cross the seam as naturally as AI targeting.
