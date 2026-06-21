# AI Social Alias Follow-ups

## Status

Investigative plan. The owner-follow teleport fix is covered by
`FollowOwnerGoalMixin` and `TamableAnimalOwnerTeleportMixin`; this plan tracks
nearby vanilla AI paths that still use raw X/Z distance, raw block positions, or
raw bounding-box overlap after entity storage and generic path requests have
already been made alias-aware.

## Problem

Minecraft AI contains many local follow, sit, social, and short-range movement
checks outside the common targeting and path-navigation APIs. In a wrapped
world, those checks can see two entities as raw-far even when they are visibly
near through a tile seam, or can aim a short movement at the raw canonical copy
instead of the actor-local alias.

The existing generic hooks reduce the blast radius:

- `ActorLocalTargets` provides actor-local target positions, hitboxes, and
  wrapped distances.
- `PathNavigationMixin`, `GroundPathNavigationMixin`, and
  `FlyingPathNavigationMixin` make `moveTo(entity, ...)` and
  `createPath(entity, ...)` target useful aliases.
- `LookControlMixin` and `MobLookMixin` handle common entity-looking paths.
- Combat targeting, sensing, melee, ranged goals, POI path targets, projectiles,
  pickup, and interactions already have narrower documented hooks.

The remaining hazards are mostly bespoke goal gates, manually computed deltas,
and block-position choices.

## Audited Vanilla Sources

Common source anchors in Minecraft 26.1.2:

- `net/minecraft/world/entity/ai/goal/SitWhenOrderedToGoal.java`
- `net/minecraft/world/entity/animal/feline/Cat.java`
  (`CatRelaxOnOwnerGoal`)
- `net/minecraft/world/entity/ai/goal/LandOnOwnersShoulderGoal.java`
- `net/minecraft/world/entity/ai/goal/TemptGoal.java`
- `net/minecraft/world/entity/ai/goal/FollowParentGoal.java`
- `net/minecraft/world/entity/ai/goal/FollowMobGoal.java`
- `net/minecraft/world/entity/ai/goal/LlamaFollowCaravanGoal.java`
- `net/minecraft/world/entity/ai/goal/FollowPlayerRiddenEntityGoal.java`
- `net/minecraft/world/entity/ai/goal/LeapAtTargetGoal.java`
- `net/minecraft/world/entity/ai/goal/OcelotAttackGoal.java`
- Brain/social behavior candidates:
  `AnimalMakeLove`, `BabyFollowAdult`, `BehaviorUtils`, `FollowTemptation`,
  `InteractWith`, `SetEntityLookTarget`, `SetLookAndInteract`,
  `LookAndFollowTradingPlayerSink`, `ShowTradesToPlayer`, and
  `SocializeAtBell`.

## Fix Plan

### Phase 1: Tameable Owner-adjacent Behavior

Goal: finish tameable owner behavior that is close to the wolf teleport bug.

1. Add `SitWhenOrderedToGoalMixin`.
   - Wrap `TamableAnimal.distanceToSqr(owner)` in `canUse()`.
   - Use `ActorLocalTargets.distanceToSqr(mob, owner)`.
   - Expected behavior: an ordered-sitting pet visibly near an attacked owner
     can leave sitting just as it would in vanilla raw-near space.

2. Add a cat owner-bed hook for `Cat.CatRelaxOnOwnerGoal`.
   - Wrap the owner distance gate in `canUse()` and the close-enough gate in
     `tick()`.
   - Project `ownerPlayer.blockPosition()` into the cat's nearest alias before
     resolving the bed-adjacent goal position.
   - Keep gift generation based on the cat's canonical post-sleep position.
   - Expected behavior: a tame cat can find and lie near the owner's visible bed
     across a seam.

3. Add `LandOnOwnersShoulderGoalMixin`.
   - Replace raw `entity.getBoundingBox().intersects(owner.getBoundingBox())`
     with an actor-local owner box check.
   - Use `ActorLocalTargets.box(entity, owner)` or
     `TopologicalEntityQueries.nearestAliasBox(...)`.
   - Expected behavior: a parrot that visibly overlaps its owner through an
     alias can mount the shoulder.

### Phase 2: Passive Follow And Spacing Goals

Goal: fix social follow goals whose path requests may already target aliases,
but whose start, stop, and spacing decisions still use raw distance.

1. Add `FollowParentGoalMixin`.
   - Gather parent candidates through topological entity queries or keep the raw
     query and rank with `ActorLocalTargets.distanceToSqr(...)`.
   - Wrap `canContinueToUse()` distance gates.
   - `moveTo(parent, ...)` can remain on the existing navigation hooks.

2. Add `FollowMobGoalMixin`.
   - Gather nearby mobs through topological queries, deduping canonical entity
     identity.
   - Wrap `canContinueToUse()` and the tick-time manually computed distance.
   - Replace the "back away from followed mob" X/Z delta with wrapped deltas or
     the followed mob's nearest alias position.

3. Add `LlamaFollowCaravanGoalMixin`.
   - Rank caravan head candidates with wrapped distance.
   - Wrap the far-distance acceleration/dropout gate.
   - Replace tick-time `distanceTo(...)` and `Vec3(follows - llama)` with an
     actor-local alias vector.

4. Add `FollowPlayerRiddenEntityGoalMixin`.
   - Query nearby ridden entities through topological entity queries.
   - Project `following.blockPosition()` to the follower-local alias before
     building behind/in-front block targets.
   - Wrap the 4-block and 12-block distance mode switches.

### Phase 3: Temptation And Short-range Animal Movement

Goal: make held-food and short leap/attack decisions match visible seam
geometry.

1. Add `TemptGoalMixin`.
   - `getNearestPlayer(...)` may already benefit from wrapped player lookup, but
     the goal's own stop/scare distances need `ActorLocalTargets.distanceToSqr`.
   - Store the player's actor-local X/Z when recording scare reference
     positions.
   - For `TemptGoal.ForNonPathfinders`, aim move control at the player's
     nearest alias eye position before random interpolation.

2. Add `LeapAtTargetGoalMixin`.
   - Wrap the 2-to-4-block launch-distance gate.
   - Build the launch vector toward `ActorLocalTargets.position(mob, target)`
     instead of raw target X/Z.

3. Add `OcelotAttackGoalMixin`.
   - Wrap continue and tick attack-distance gates.
   - Use the target's nearest alias position for speed choice and melee reach.
   - Keep `moveTo(target, ...)` on the existing path-navigation hooks.

### Phase 4: Brain/social Behaviors

Goal: audit memory-driven behaviors separately because they use a different
framework and some memory positions intentionally remain canonical.

1. Build a caller table for brain behaviors that compare two entities or an
   entity and `EntityTracker`, including `AnimalMakeLove`,
   `BabyFollowAdult`, `FollowTemptation`, `InteractWith`,
   `SetEntityLookTarget`, `SetLookAndInteract`,
   `LookAndFollowTradingPlayerSink`, `ShowTradesToPlayer`, and
   `SocializeAtBell`.
2. Classify each as:
   - entity-to-entity and safe to wrap;
   - entity-to-block/POI and already covered by POI helpers;
   - memory-position logic that should remain canonical.
3. Implement only the entity-to-entity paths first, using
   `ActorLocalTargets.distanceToSqr(...)` and actor-local `EntityTracker`
   positions where needed.

## Regression Matrix

Manual tests should use a small tile so each case can be set up on opposite
visible sides of an X seam, a Z seam, and a corner seam.

| Case | Setup | Expected Result |
| --- | --- | --- |
| Sitting pet owner attacked | Ordered-sitting wolf/cat/parrot near owner through seam; owner is hit. | Pet behaves like vanilla raw-near space and may stand/respond. |
| Cat owner bed | Tame cat and sleeping owner/bed visible-near across seam. | Cat navigates to visible bed side and lies down. |
| Parrot shoulder | Tame parrot overlaps owner through seam. | Parrot can mount shoulder. |
| Baby follows adult | Baby and adult same species visible-near/far across seam. | Baby chooses nearest visible adult and stops at vanilla distance. |
| Parrot follows mob | Parrot near different mob through seam. | Follow/spacing does not jitter or back away in raw direction. |
| Llama caravan | Leashed/caravan llamas cross a seam. | Chain spacing and speed gates use visible distance. |
| Food temptation | Animal follows a player holding food across a seam. | It approaches and stops using visible distance. |
| Leap/ocelot attack | Cat-like leap/ocelot target across seam. | Leap and melee decisions aim at visible target alias. |
| Ridden-follow mob | Mob with `FollowPlayerRiddenEntityGoal` follows a player-controlled mount through seam. | Behind/ahead target blocks are in follower-local alias frame. |

## Implementation Notes

- Prefer narrow mixins at audited call sites over changing global entity
  distance behavior.
- Use `ActorLocalTargets.distanceToSqr(...)` for actor-to-entity gates.
- Use `ActorLocalTargets.position(...)` or `nearestAliasPosition(...)` for
  manually computed movement vectors.
- Use `ActorLocalTargets.nearestAliasBlockPos(...)` or
  `TopologyContext.virtualBlockForViewer(...)` when vanilla converts a target
  entity to `blockPosition()`.
- Use `TopologicalEntityQueries` only for local broad-phase queries whose side
  effects are safe with deduped canonical identity.
- Update `docs/mod-mechanics/entities.md`,
  `docs/vanilla-mechanics/mobs-and-entities.md`, and the seam behavior
  checklist as phases land.
