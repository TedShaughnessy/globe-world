# Ranged Mob Alias Combat Audit

## Status

Partially implemented. Target acquisition, pursuit, ranged attack readiness,
common ranged launch vectors, creeper swelling, and several custom combat gates
are alias-aware. Arrow entity collision now tests wrapped entity hitboxes for
vanilla arrow movement. Other projectile travel/collision, shulker-bullet
homing, phantom attack anchors, and some raw custom target-search boxes remain
open.

## Prompt

During playtesting, a skeleton behaved unexpectedly while the player and
skeleton appeared to be in the same tile. Mobs otherwise track players across
tile aliases correctly, so the problem is not simply target acquisition.

During later playtesting, a creeper pathfound to a player outside the canonical
tile but did not start or complete its explosion. That has the same diagnostic
shape: alias-aware pursuit succeeds, then the attack-specific readiness logic
falls back to raw target geometry.

## Current Coverage

The AI alias pass covers many target-selection, goal-distance, and launch-vector
decisions:

- `AiAliasUtil` computes nearest target aliases, alias hitboxes, and wrapped
  AI distances.
- `TargetingConditionsMixin`, `ServerEntityGetterMixin`, and
  `NearestLivingEntitySensorMixin` wrap target eligibility and candidate
  ordering distances.
- `SensingMixin` makes `Sensing.hasLineOfSight(...)` accept alias line of sight
  when vanilla raw sight fails.
- `LookControlMixin` and `MobLookMixin` turn mobs toward the nearest target
  alias for ordinary look-control calls.
- `PathNavigationMixin`, `GroundPathNavigationMixin`, and
  `FlyingPathNavigationMixin` redirect entity path targets to useful aliases.
- `RangedAttackGoalMixin`, `RangedBowAttackGoalMixin`, and
  `RangedCrossbowAttackGoalMixin` wrap the standard ranged-goal distance checks.
- `AbstractSkeletonRangedAttackMixin`, `IllusionerRangedAttackMixin`,
  `DrownedRangedAttackMixin`, `SnowGolemRangedAttackMixin`,
  `LlamaRangedAttackMixin`, `WitchRangedAttackMixin`,
  `CrossbowItemRangedAttackMixin`, `BlazeAttackGoalMixin`,
  `GhastShootFireballGoalMixin`, `WitherBossRangedAttackMixin`, and
  `BreezeShootMixin` aim common ranged launches at the nearest target alias.
- `SwellGoalMixin`, `GuardianAttackGoalMixin`,
  `GuardianAttackSelectorMixin`, and `ShulkerAttackGoalMixin` wrap close-range
  or custom attack readiness gates that do not go through the standard melee or
  ranged goals.

That coverage is enough for many mobs to notice, chase, face, and start attack
windup against a player whose nearest visible alias differs from raw storage
coordinates, then aim the first projectile or special attack at that alias.

## Skeleton Finding

If a skeleton and player are truly in the same server coordinate frame, vanilla
bow aim should be correct for that shot. `AbstractSkeleton.performRangedAttack`
computes:

```java
double xd = target.getX() - this.getX();
double yd = target.getY(0.3333333333333333) - arrow.getY();
double zd = target.getZ() - this.getZ();
```

That only becomes wrong when the skeleton's storage position and the player's
effective visual position are in different tile frames. In Globe World, that can
still look like "same tile" from the client if the player is seeing a
viewer-relative entity packet or a presentation-only visual alias. Use
`/globeworld entity <target>` while reproducing to compare the mob's raw,
canonical, and target-alias distances.

For a same-frame skeleton shot that still appears wrong, the more likely issues
are projectile travel/collision after launch, a visual-alias confusion, or
vanilla bow timing:

- `RangedBowAttackGoal` requires stable sight before stopping, drawing, and
  releasing. Short interruptions reset or delay the shot.
- Arrows are canonicalized like other non-player entities before storage and
  after server ticks. Their trajectory vector survives.
- `AbstractArrow.tick` raycasts blocks and entities between raw `position()` and
  `position() + deltaMovement`. Globe World supplements the entity portion with
  nearest-alias hitbox tests, but block clipping is still raw.

## Cross-Tile Projectile Launch Coverage

These mobs use alias target X/Z before computing the final projectile vector:

- Skeletons and strays: `AbstractSkeleton.performRangedAttack`.
- Illusioners: `Illusioner.performRangedAttack`.
- Drowned with tridents: `Drowned.performRangedAttack`.
- Snow golems: `SnowGolem.performRangedAttack`.
- Llamas and trader llamas: `Llama.spit`.
- Witches: `Witch.performRangedAttack`, including potion choice distance.
- Pillagers and piglins with crossbows: `CrossbowAttackMob.performCrossbowAttack`
  delegates to `CrossbowItem.shootProjectile`, which uses raw
  `targetOverride.getX()/getZ()`.
- Blazes: `Blaze.BlazeAttackGoal`.
- Ghasts: `GhastShootFireballGoal`, plus target-facing in
  `Ghast.faceMovementDirection`.
- Withers: main and side-head wither-skull targeting.
- Breezes: `Shoot`.

Y calculations, target leading, potion selection, charge timing, and vanilla
inaccuracy are intentionally preserved. Projectile physics after launch remains
separate.

## Target Search Box Risks

Some target acquisition paths are only partly covered. `NearestAttackableTargetGoal`
uses `ServerEntityGetter.getNearestPlayer(...)` for players, which scans the
server player list and lets `TargetingConditionsMixin` apply wrapped distance.
For non-player targets, vanilla first builds a raw AABB candidate list with
`Level.getEntitiesOfClass(...)`; alias-aware sorting cannot recover a target that
was excluded by that raw box.

Custom goals can also perform their own raw nearby scans. Phantom target
selection, for example, calls `ServerEntityGetter.getNearbyPlayers(...)` with a
raw inflated box before `TargetingConditions` runs. Shulker custom target areas
and guardian selectors deserve the same style of review.

Expected symptom: a mob may track players correctly in one path but miss wrapped
nearby non-player targets, or a custom mob may fail to acquire a wrapped-near
player because its first candidate box was not expanded or duplicated into alias
frames.

## Cross-Tile Projectile Travel Risks

All non-player projectiles are continuously canonicalized through
`EntityCanonicalizer`. That prevents duplicate projectile storage but does not
make projectile physics fully toroidal.

Vanilla projectile movement uses raw block/entity collision:

- `ProjectileUtil.getHitResultOnMoveVector(...)` clips from raw position to raw
  next position and queries raw `Level.getEntities(...)`.
- `AbstractArrow.tick`, `ThrowableProjectile.tick`,
  `AbstractHurtingProjectile.tick`, and `ShulkerBullet.tick` all depend on that
  raw move-vector hit result.

`AbstractArrowAliasCollisionMixin` now wraps the `AbstractArrow.findHitEntities`
entity path. It keeps vanilla hits, queries canonical candidate boxes, scans
server players that may live in raw alias coordinates, and tests each real
entity's nearest alias hitbox via `ProjectileAliasUtil`. The resulting hit still
targets the real entity, so vanilla damage, deflection, piercing, and pickup
logic own the result.

`ThrownSplashPotionAliasEffectMixin` wraps splash-potion effect application
after impact. It adds wrapped living-entity candidates to vanilla's raw
`effectAabb` query and measures potion falloff against each candidate's nearest
alias box, so witch-thrown splash potions can apply status effects to players
and mobs in alias tiles.

Expected remaining symptom: a projectile that should cross a tile edge may
canonicalize back into the canonical tile but miss the wrapped block it should
have hit, hit a raw obstacle in the wrong frame, or miss wrapped entity
collision/effects for projectile classes that do not yet have a targeted wrapper.

## Remaining Custom Ranged Goal Gaps

Several mobs do not use the standard `RangedAttackGoal`/`RangedBowAttackGoal`
path for all attack logic, so the current ranged-goal mixins do not fully cover
them:

- Guardian and elder guardian: server attack gates use alias distance/sight, but
  client beam visuals still use raw target coordinates.
- Shulker: attack range uses alias distance, but `ShulkerBullet` homes via raw
  target block positions and raw move-vector collision.
- Wither: side-head attack gates and skull vectors use aliases, but side-head
  visual tracking and movement steering still use raw target coordinates.
- Breeze: `Shoot` uses alias range, look, and wind-charge vectors, but
  `BreezeUtil` jump/slide line-of-sight checks still use raw `Vec3` targets.
- Phantom: target selection uses `TargetingConditions` for range/sight, but
  attack anchors and sweep targets use raw target block/position coordinates.

Expected symptoms vary by mob: some may fail to start attacks across a seam,
some may attack through/around the wrong obstacle, and some may launch correctly
only when the raw and nearest-alias frames happen to coincide.

## Close-Range Special Attack Coverage

Some non-ranged attacks have their own readiness gates outside
`MeleeAttackGoal`, so the melee reach mixin does not cover them by itself.

Creepers use `SwellGoal` before exploding:

- `SwellGoalMixin` wraps `canUse()` so swelling starts when
  `creeper.distanceToSqr(target) < 9.0`.
- `SwellGoalMixin` wraps `tick()` so swelling continues or cancels when
  `creeper.distanceToSqr(target) > 49.0` or when sensing reports no line of
  sight.
- `Creeper.explodeCreeper()` explodes at the creeper's own raw/canonical
  position, so the alias-sensitive part is the swell gate rather than the final
  explosion center.

Expected symptom: the creeper can acquire and path to a player through the
nearest alias, but it never primes, or it starts priming and then backs out,
because raw storage coordinates remain outside the vanilla 3-block start or
7-block continuation thresholds.

## Line-Of-Sight Coverage

`SensingMixin` now returns true only when vanilla raw line of sight succeeds or
`AiAliasUtil.aliasLineOfSight(...)` proves the nearest-alias ray is clear. The
old "wrapped distance is shorter" fallback is no longer a blanket substitute for
visibility.

## Remaining Fix Shape

The implemented launch-vector pass uses shared `AiAliasUtil` nearest-alias
helpers instead of per-mob coordinate math. Remaining work:

- Wrap or supplement raw target-search AABBs where custom goals do not already
  start from the full player list.
- Continue projectile collision wrapping: launch-vector fixes make first-frame
  aim correct, arrows now have wrapped entity hit tests, and splash potions now
  have wrapped effect recipients, but arrow/potion block clipping plus
  fireballs, wind charges, wither skulls, and bullets still need wrapped
  travel/collision coverage.
- Audit homing and anchor systems that derive target `BlockPos` values from raw
  X/Z, especially shulker bullets and phantom attacks.

## Test Matrix

Minimum playtest cases:

- Skeleton in same storage tile as player, with `/globeworld entity` confirming
  raw and alias distances agree.
- Skeleton across an X seam and a Z seam, with no visual entity aliases enabled.
- Skeleton shot where the projectile crosses a tile edge before reaching the
  player.
- Creeper across an X seam and a Z seam: confirm it pathfinds, primes within
  wrapped 3-block range, keeps swelling within wrapped 7-block range, and
  explodes at its canonical position.
- Pillager, drowned, witch, blaze, ghast, shulker, guardian, wither, breeze, and
  phantom across one seam.
- Obstructed seam case: target is wrapped-near but a wall blocks the nearest
  alias ray.

## Related Mechanics

- [Entities](../mod-mechanics/entities.md)
- [Vanilla mobs and entities](../vanilla-mechanics/mobs-and-entities.md)
