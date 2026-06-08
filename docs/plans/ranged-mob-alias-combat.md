# Ranged Mob Alias Combat Audit

## Status

Investigative. Target acquisition and pursuit are partly alias-aware, but
projectile launch, projectile travel, creeper swelling, and several custom
combat goals still use raw vanilla X/Z coordinates.

## Prompt

During playtesting, a skeleton behaved unexpectedly while the player and
skeleton appeared to be in the same tile. Mobs otherwise track players across
tile aliases correctly, so the problem is not simply target acquisition.

During later playtesting, a creeper pathfound to a player outside the canonical
tile but did not start or complete its explosion. That has the same diagnostic
shape: alias-aware pursuit succeeds, then the attack-specific readiness logic
falls back to raw target geometry.

## Current Coverage

The existing AI alias pass covers many target-selection and goal-distance
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

That coverage is enough for many mobs to notice, chase, face, and start attack
windup against a player whose nearest visible alias differs from raw storage
coordinates.

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
  after server ticks. Their trajectory vector survives, but raycast/collision is
  still raw.
- `AbstractArrow.tick` raycasts blocks and entities between raw `position()` and
  `position() + deltaMovement`. It does not test wrapped copies of blocks,
  players, or mobs.

## Cross-Tile Projectile Launch Risks

These mobs use the standard ranged goals for readiness but compute the final
projectile vector from raw target coordinates:

- Skeletons and strays: `AbstractSkeleton.performRangedAttack`.
- Illusioners: `Illusioner.performRangedAttack`.
- Drowned with tridents: `Drowned.performRangedAttack`.
- Snow golems: `SnowGolem.performRangedAttack`.
- Llamas and trader llamas: `Llama.spit`.
- Witches: `Witch.performRangedAttack`, including potion choice distance.
- Pillagers and piglins with crossbows: `CrossbowAttackMob.performCrossbowAttack`
  delegates to `CrossbowItem.shootProjectile`, which uses raw
  `targetOverride.getX()/getZ()`.

Expected symptom: the mob can correctly track and begin attacking across a tile,
but the projectile launches toward the raw/canonical target instead of the
nearest visible alias. Depending on tile size and where the entities stand, this
can look like firing sideways, firing behind the player, or never hitting.

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
make projectile physics toroidal.

Vanilla projectile movement uses raw block/entity collision:

- `ProjectileUtil.getHitResultOnMoveVector(...)` clips from raw position to raw
  next position and queries raw `Level.getEntities(...)`.
- `AbstractArrow.tick`, `ThrowableProjectile.tick`,
  `AbstractHurtingProjectile.tick`, and `ShulkerBullet.tick` all depend on that
  raw move-vector hit result.

Expected symptom: a projectile that should cross a tile edge may canonicalize
back into the canonical tile but miss the wrapped block/player/entity it should
have hit, or hit a raw obstacle in the wrong frame.

## Custom Ranged Goal Gaps

Several mobs do not use the standard `RangedAttackGoal`/`RangedBowAttackGoal`
path for all attack logic, so the current ranged-goal mixins do not fully cover
them:

- Blaze: `Blaze.BlazeAttackGoal` uses raw `distanceToSqr(target)`, raw
  `target.getX()/getZ()` for movement, and raw fireball direction.
- Ghast: `GhastShootFireballGoal` uses raw `target.distanceToSqr(ghast)`, direct
  `ghast.hasLineOfSight(target)`, and raw fireball direction. `Ghast.faceMovementDirection`
  also turns toward raw target X/Z.
- Guardian and elder guardian: `GuardianAttackGoal` calls direct
  `guardian.hasLineOfSight(target)` rather than `Sensing`, and client beam
  visuals use raw target coordinates.
- Shulker: `ShulkerAttackGoal` uses raw `distanceToSqr(target)` for the 20-block
  attack gate. `ShulkerBullet` homes via raw target block positions and raw
  move-vector collision.
- Wither: side-head targeting uses raw `distanceToSqr`, direct
  `hasLineOfSight`, raw head tracking, and raw wither-skull vectors.
- Breeze: `Shoot` uses raw `position().distanceToSqr(target.position())`, raw
  `lookAt(..., target.position())`, and raw wind-charge vectors. `BreezeUtil`
  line-of-sight checks use raw `Vec3` targets.
- Phantom: target selection uses `TargetingConditions` for range/sight, but
  attack anchors and sweep targets use raw target block/position coordinates.

Expected symptoms vary by mob: some may fail to start attacks across a seam,
some may attack through/around the wrong obstacle, and some may launch correctly
only when the raw and nearest-alias frames happen to coincide.

## Close-Range Special Attack Gaps

Some non-ranged attacks have their own readiness gates outside
`MeleeAttackGoal`, so the current melee reach mixin does not cover them.

Creepers use `SwellGoal` before exploding:

- `SwellGoal.canUse()` starts swelling only when
  `creeper.distanceToSqr(target) < 9.0`.
- `SwellGoal.tick()` cancels swelling when
  `creeper.distanceToSqr(target) > 49.0` or when sensing reports no line of
  sight.
- `Creeper.explodeCreeper()` explodes at the creeper's own raw/canonical
  position, so the observed failure is likely the swell gate rather than the
  final explosion center.

Expected symptom: the creeper can acquire and path to a player through the
nearest alias, but it never primes, or it starts priming and then backs out,
because raw storage coordinates remain outside the vanilla 3-block start or
7-block continuation thresholds.

## Line-Of-Sight Concern

`SensingMixin` currently returns true when `AiAliasUtil.aliasLineOfSight(...)`
is true, or when `AiAliasUtil.wrappedHorizontalDistanceIsShorter(...)` is true.
The second fallback can make a mob treat a wrapped target as visible even if the
alias clip did not prove visibility. That may have been useful while debugging
seam sight, but for ranged mobs it can cause a bow/crossbow goal to charge and
shoot at a target that should be blocked.

This does not explain an ordinary same-frame skeleton shot by itself, because
vanilla raw line of sight should already handle that case. It can explain ranged
mobs deciding to fire after crossing tile frames or around tile edges.

## Proposed Fix Shape

Use one shared "ranged alias target" helper instead of patching each mob with
slightly different math:

- Given `shooter`, `target`, and optional projectile spawn position, return the
  target's nearest alias position relative to the shooter or projectile origin.
- Add the same convention for special non-projectile combat readiness checks,
  starting with `SwellGoal`'s 3-block start and 7-block continuation gates.
- Use the helper in bow, trident, snowball, spit, potion, fireball, skull,
  wind-charge, and crossbow launch sites before calculating X/Z direction.
- Keep Y calculations vanilla unless a mob uses a target `BlockPos` derived from
  X/Z, such as shulker bullets and phantom anchors.
- Wrap or supplement raw target-search AABBs where custom goals do not already
  start from the full player list.
- Tighten `SensingMixin` so the "wrapped distance is shorter" fallback is not a
  blanket substitute for line-of-sight in ranged attack decisions.
- Add projectile collision wrapping separately: launch-vector fixes will make
  first-frame aim correct, but arrows, potions, fireballs, and bullets still need
  wrapped block/entity hit tests when they cross tile edges.

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
