# AI Social Alias Follow-ups

## Status

Partially implemented plan. The owner-follow teleport fix is already covered by
`FollowOwnerGoalMixin` and `TamableAnimalOwnerTeleportMixin`, and Batch 1 is now
implemented by `SitWhenOrderedToGoalMixin`, `LandOnOwnersShoulderGoalMixin`,
and `CatRelaxOnOwnerGoalMixin`. This plan tracks the remaining nearby audited
vanilla AI hazards as small, reviewable batches.

The first implementation pass should cover goal-based AI only. Brain/memory
behaviors remain a separate audit batch because some memory positions are
canonical by design and should not be wrapped blindly.

## Review Findings

Existing alias infrastructure is sufficient for most of this work:

- `ActorLocalTargets.distanceToSqr(...)` covers actor-to-entity and
  actor-to-coordinate gates.
- `ActorLocalTargets.nearestAliasPosition(...)`,
  `nearestAliasEyePosition(...)`, and `nearestAliasBlockPos(...)` cover manual
  movement vectors and entity-derived block targets.
- `ActorLocalTargets.box(...)` and `TopologicalEntityQueries.nearestAliasBox(...)`
  cover actor-local hitbox overlap checks.
- `TopologicalEntityQueries.entities(...)` and `entitiesOfClass(...)` cover
  local broad-phase queries when vanilla's raw `getEntities*` scan is too small
  near a seam.
- Existing mixins use narrow `@WrapOperation` hooks and are registered in
  `mod-fabric/src/main/resources/globe-world.mixins.json`; follow that style.

Important corrections from the source review:

- `SitWhenOrderedToGoal` only needs the owner distance in `canUse()`;
  `canContinueToUse()` only checks the ordered-sit flag.
- `Cat.CatRelaxOnOwnerGoal` needs more than distance wrapping: the sleeping
  player's bed block position, the bed-adjacent `goalPos`, and the occupied-cat
  query all need to stay in the cat-local visible frame while block reads still
  resolve to the canonical bed.
- `FollowMobGoal`, `LlamaFollowCaravanGoal`, `FollowPlayerRiddenEntityGoal`,
  `TemptGoal.ForNonPathfinders`, and `LeapAtTargetGoal` contain hand-written
  X/Z vectors. These are higher risk than simple distance wrappers and should
  be implemented after the direct owner-adjacent hooks.

## Implementation Order

### Batch 1: Owner-adjacent Tameable Behavior

Status: implemented, pending user-run build and manual seam regression.

Goal: finish the tameable owner cases closest to the fixed owner-follow bug.

Deliverables:

1. `SitWhenOrderedToGoalMixin`.
   - Target: `net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal`.
   - Hook: wrap `TamableAnimal.distanceToSqr(Entity)` in `canUse()`.
   - Replacement: `ActorLocalTargets.distanceToSqr(tamable, owner)`.
   - Register the mixin in `globe-world.mixins.json`.
   - Expected behavior: an ordered-sitting tame mob visibly near an attacked
     owner through a seam uses the same stand/respond decision as vanilla
     raw-near space.

2. `LandOnOwnersShoulderGoalMixin`.
   - Target: `net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal`.
   - Shadow `ShoulderRidingEntity entity`.
   - Hook: wrap the owner `getBoundingBox()` call in `tick()`, or wrap the
     `AABB.intersects(...)` call if that gives a cleaner operand order.
   - Replacement: compare the entity box to `ActorLocalTargets.box(entity,
     owner)`.
   - Register the mixin in `globe-world.mixins.json`.
   - Expected behavior: a parrot that visibly overlaps its owner through an
     alias can mount the shoulder.

3. `CatRelaxOnOwnerGoalMixin`.
   - Target: inner class
     `net.minecraft.world.entity.animal.feline.Cat$CatRelaxOnOwnerGoal`.
   - Shadow `Cat cat`.
   - Hook the `canUse()` owner distance gate:
     `Cat.distanceToSqr(Entity)` -> `ActorLocalTargets.distanceToSqr(cat,
     ownerPlayer)`.
   - Hook the `tick()` close-enough gate:
     `Cat.distanceToSqr(Entity)` -> `ActorLocalTargets.distanceToSqr(cat,
     ownerPlayer)`.
   - Hook owner bed lookup in `canUse()`:
     `ownerPlayer.blockPosition()` should become
     `ActorLocalTargets.nearestAliasBlockPos(cat, ownerPlayer)`.
   - Ensure the resulting `goalPos` is used as a visible-frame navigation
     target. Vanilla block reads at that alias should already canonicalize
     through the block lookup layer.
   - Replace `spaceIsOccupied()`'s raw cat query with
     `TopologicalEntityQueries.entitiesOfClass(cat.level(), Cat.class, new
     AABB(goalPos).inflate(2.0), predicate)` and filter overlap/lying state in
     the same visible frame.
   - Do not alter `giveMorningGift()` in the first pass; gift generation should
     remain based on the cat's canonical post-sleep position.
   - Register the mixin in `globe-world.mixins.json`.
   - Expected behavior: a tame cat can find the owner's visible bed side across
     a seam, navigate to the bed-adjacent position, and lie down.

Validation after Batch 1:

- Ask the user to run `./gradlew build`.
- Manual seam tests:
  sitting tame mob near attacked owner, parrot shoulder mounting, sleeping
  owner with cat and bed across X seam, Z seam, and corner seam.

Docs after Batch 1:

- Update `docs/mod-mechanics/entities.md` under tamed animal owner behavior.
- Add the three source anchors to `docs/vanilla-mechanics/mobs-and-entities.md`.
- Add or update rows in `docs/plans/seam-behavior-checklist.md`.

### Batch 2: Passive Follow And Spacing Goals

Goal: make social follow goals choose and maintain visible-near companions.

Deliverables:

1. Add `FollowParentGoalMixin`.
   - Target: `net.minecraft.world.entity.ai.goal.FollowParentGoal`.
   - Shadow `Animal animal` and `Animal parent`.
   - Replace the parent candidate query in `canUse()` with
     `ActorLocalTargets.targetsInActorRange(animal, animal.getClass(),
     animal.getBoundingBox().inflate(8.0, 4.0, 8.0), predicate)`.
   - Rank parent candidates with `ActorLocalTargets.distanceToSqr(animal,
     candidate)`.
   - Wrap the `canContinueToUse()` distance gate with
     `ActorLocalTargets.distanceToSqr(animal, parent)`.
   - Leave `moveTo(parent, ...)` on existing navigation hooks.

2. Add `FollowMobGoalMixin`.
   - Target: `net.minecraft.world.entity.ai.goal.FollowMobGoal`.
   - Shadow `Mob mob`, `Mob followingMob`, `PathNavigation navigation`, and
     `float stopDistance`.
   - Replace `canUse()`'s raw `getEntitiesOfClass(Mob.class, ...)` scan with
     `ActorLocalTargets.targetsInActorRange(...)`, preserving the vanilla
     class-difference predicate and invisible-mob skip.
   - Wrap `canContinueToUse()` distance with actor-local distance.
   - In `tick()`, replace raw `mob - followingMob` distance math with the
     following mob's actor-local alias position.
   - Replace the back-away vector with a vector from the mob to that alias, then
     move to the mirrored local position.
   - Keep look control and `moveTo(followingMob, ...)` on existing hooks.

3. Add `LlamaFollowCaravanGoalMixin`.
   - Target: `net.minecraft.world.entity.ai.goal.LlamaFollowCaravanGoal`.
   - Shadow `Llama llama`, `double speedModifier`, and `int distCheckCounter`.
   - Replace `canUse()`'s raw query with
     `TopologicalEntityQueries.entities(...)` over
     `llama.getBoundingBox().inflate(9.0, 4.0, 9.0)`, preserving the llama and
     trader-llama filter.
   - Rank caravan candidates with `ActorLocalTargets.distanceToSqr(llama,
     candidate)`.
   - Wrap the `canContinueToUse()` far-distance gate with actor-local distance.
   - In `tick()`, compute the caravan-head alias position with
     `ActorLocalTargets.nearestAliasPosition(llama, follows)` and build the
     normalized delta toward that alias.

4. Add `FollowPlayerRiddenEntityGoalMixin`.
   - Target: `net.minecraft.world.entity.ai.goal.FollowPlayerRiddenEntityGoal`.
   - Shadow `PathfinderMob mob`, `Class<? extends Entity> entityTypeToFollow`,
     and `Player following`.
   - Replace the raw ridden-entity scans in `canUse()` and `start()` with
     `TopologicalEntityQueries.entitiesOfClass(...)` over the mob-local
     5-block box.
   - In `tick()`, replace `following.blockPosition()` with
     `ActorLocalTargets.nearestAliasBlockPos(mob, following)` before building
     behind/ahead positions.
   - Wrap the 4-block and 12-block `mob.distanceTo(following)` mode switches
     with `Math.sqrt(ActorLocalTargets.distanceToSqr(mob, following))` or
     squared threshold comparisons if the bytecode hook permits it cleanly.

Validation after Batch 2:

- Ask the user to run `./gradlew build`.
- Manual tests:
  baby/adult follow across seams, parrot or similar `FollowMobGoal` user
  spacing across seams, llama caravan crossing an X seam and a corner seam, and
  ridden-follow mobs choosing behind/ahead targets in the visible frame.

Docs after Batch 2:

- Move the durable behavior summary into `docs/mod-mechanics/entities.md`.
- Add vanilla anchors for `FollowParentGoal`, `FollowMobGoal`,
  `LlamaFollowCaravanGoal`, and `FollowPlayerRiddenEntityGoal`.

### Batch 3: Temptation And Short-range Animal Movement

Goal: make held-food, leap, and ocelot-style attack decisions match visible
seam geometry after target acquisition has already accepted an entity.

Deliverables:

1. Add `TemptGoalMixin`.
   - Target: `net.minecraft.world.entity.ai.goal.TemptGoal`.
   - Shadow `Mob mob`, `Player player`, and scare-reference fields `px`, `py`,
     `pz`.
   - Wrap `mob.distanceToSqr(player)` in `canContinueToUse()` and `tick()` with
     `ActorLocalTargets.distanceToSqr(mob, player)`.
   - When vanilla stores scare reference coordinates in `start()` and in the
     far branch of `canContinueToUse()`, store the player's mob-local alias X/Z
     and vanilla Y.
   - When vanilla checks `player.distanceToSqr(px, py, pz)`, compare the current
     player alias in the same mob-local frame against the stored alias
     reference.
   - Leave `navigateTowards(player)` on the existing path-navigation hooks for
     pathfinding mobs.

2. Add `TemptGoalForNonPathfindersMixin`.
   - Target: inner class
     `net.minecraft.world.entity.ai.goal.TemptGoal$ForNonPathfinders`.
   - Hook `navigateTowards(Player)`.
   - Replace `player.getEyePosition()` in the interpolation target with
     `ActorLocalTargets.nearestAliasEyePosition(mob, player)`.
   - Preserve vanilla random interpolation and move-control speed.

3. Add `LeapAtTargetGoalMixin`.
   - Target: `net.minecraft.world.entity.ai.goal.LeapAtTargetGoal`.
   - Shadow `Mob mob` and `LivingEntity target`.
   - Wrap the `canUse()` 2-to-4-block distance gate with
     `ActorLocalTargets.distanceToSqr(mob, target)`.
   - In `start()`, replace target X/Z with
     `ActorLocalTargets.nearestAliasPosition(mob, target)` while preserving the
     vanilla Y impulse.

4. Add `OcelotAttackGoalMixin`.
   - Target: `net.minecraft.world.entity.ai.goal.OcelotAttackGoal`.
   - Shadow `Mob mob` and `LivingEntity target`.
   - Wrap `canContinueToUse()`'s 15-block distance gate.
   - In `tick()`, replace
     `mob.distanceToSqr(target.getX(), target.getY(), target.getZ())` with a
     distance to the target alias.
   - Keep look control and `moveTo(target, ...)` on existing hooks.
   - Keep melee reach and attack timing unchanged except for the alias-aware
     distance value.

Validation after Batch 3:

- Ask the user to run `./gradlew build`.
- Manual tests:
  food temptation across X/Z/corner seams, non-pathfinder temptation if a
  vanilla user can be set up, cat leap across a seam, and ocelot/cat attack
  distance and speed bands across a seam.

Docs after Batch 3:

- Update `docs/mod-mechanics/entities.md` with temptation, leap, and ocelot
  attack behavior.
- Add source anchors to `docs/vanilla-mechanics/mobs-and-entities.md`.
- Mark the corresponding seam checklist rows as implemented/manual regression
  candidates.

### Batch 4: Brain And Memory-driven Social Behaviors

Goal: audit brain behaviors after the goal-based work is stable. Do not
implement this batch until the caller table is written.

Deliverables:

1. Create a caller table covering:
   - `AnimalMakeLove`
   - `BabyFollowAdult`
   - `BehaviorUtils`
   - `FollowTemptation`
   - `InteractWith`
   - `SetEntityLookTarget`
   - `SetLookAndInteract`
   - `LookAndFollowTradingPlayerSink`
   - `ShowTradesToPlayer`
   - `SocializeAtBell`
2. For each caller, classify every coordinate/distance use as one of:
   - entity-to-entity and safe to wrap with actor-local aliases;
   - entity-to-block or POI and already covered by POI helpers;
   - memory-position logic that should remain canonical;
   - uncertain and requiring a source-level note before implementation.
3. Implement only the entity-to-entity paths after classification.
4. Keep block/POI memories canonical unless the audit proves the memory is
   render-facing or actor-local.

Validation after Batch 4:

- Ask the user to run `./gradlew build`.
- Manual tests:
  breeding partner choice across seams, baby-brain following across seams,
  villager/trader look and interaction behavior across seams, and bell/social
  behavior near a seam if a crisp setup is practical.

## General Implementation Rules

- Prefer one mixin per vanilla goal or inner goal class.
- Prefer `@WrapOperation` at exact vanilla distance, position, hitbox, and query
  calls. Use `@Inject` only when the desired replacement cannot be expressed as
  a local operand wrapper.
- Preserve vanilla Y coordinates unless the vanilla code explicitly uses an
  entity eye position.
- Preserve canonical entity identity; only distances, positions, boxes, and
  broad-phase query boxes should move into the actor-local frame.
- Use `TopologicalEntityQueries` only for local broad-phase scans whose side
  effects operate on deduped canonical entities.
- Do not change global `Entity.distanceTo*` behavior.
- Do not claim full toroidal pathfinding; the pathfinder remains vanilla with
  alias target candidates.

## Regression Matrix

Manual tests should use a small tile so each case can be set up on opposite
visible sides of an X seam, a Z seam, and a corner seam.

| Case | Setup | Expected Result | Batch |
| --- | --- | --- | --- |
| Sitting pet owner attacked | Ordered-sitting wolf/cat/parrot near owner through seam; owner is hit. | Pet behaves like vanilla raw-near space and may stand/respond. | 1 |
| Cat owner bed | Tame cat and sleeping owner/bed visible-near across seam. | Cat navigates to visible bed side and lies down. | 1 |
| Parrot shoulder | Tame parrot overlaps owner through seam. | Parrot can mount shoulder. | 1 |
| Baby follows adult | Baby and adult same species visible-near/far across seam. | Baby chooses nearest visible adult and stops at vanilla distance. | 2 |
| Mob follows mob | A `FollowMobGoal` user near a different mob through seam. | Follow/spacing does not jitter or back away in raw direction. | 2 |
| Llama caravan | Leashed/caravan llamas cross a seam. | Chain spacing and speed gates use visible distance. | 2 |
| Ridden-follow mob | Mob with `FollowPlayerRiddenEntityGoal` follows a player-controlled mount through seam. | Behind/ahead target blocks are in follower-local alias frame. | 2 |
| Food temptation | Animal follows a player holding food across a seam. | It approaches and stops using visible distance. | 3 |
| Leap/ocelot attack | Cat-like leap/ocelot target across seam. | Leap and melee decisions aim at visible target alias. | 3 |
| Brain social behavior | Breeding, baby-brain follow, villager/trader interaction, and bell social setups near seams. | Only audited entity-to-entity paths use actor-local aliases; canonical memories stay canonical. | 4 |
