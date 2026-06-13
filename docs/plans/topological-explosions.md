# Topological Explosions Plan

This plan resolves the server-side explosion geometry gap raised in the
[Minecraft coordinate coverage audit](minecraft-coordinate-coverage-audit.md).

## Problem

Globe World virtualizes outbound explosion packets and uses wrapped distance
when deciding which players receive them, but vanilla `ServerExplosion` still
does server-authoritative geometry in raw coordinates:

- block rays add raw `BlockPos` entries to `toBlow`;
- entity damage scans a raw `AABB`;
- entity damage distance uses raw distance to the explosion center;
- exposure clips sample points to the raw center.

That can miss entities visible across a seam, apply wrong knockback directions,
and include duplicate aliases of one canonical block in larger seam-crossing
explosions.

## Goals

- Damage and knock back entities according to their nearest visible alias to the
  explosion center.
- Canonical-dedupe affected blocks before block destruction/drops.
- Run exposure clips in the same visible frame used for entity distance.
- Preserve vanilla explosion rules, gamerules, fire behavior, loot behavior,
  sound/particle side effects, and packet payload shape where possible.

## Non-Goals

- A new explosion model or different blast balance.
- Client-side explosion prediction.
- Handling dimensions where tiling is disabled.

## Vanilla Source Anchors

- `net/minecraft/server/level/ServerLevel.java`
- `net/minecraft/world/level/ServerExplosion.java`
- `net/minecraft/world/level/Explosion.java`
- `net/minecraft/world/level/ExplosionDamageCalculator.java`

## Current Globe Anchors

- `ServerLevelWorldEventMixin`
- `WorldEventPacketUtil`
- `TopologicalEntityQueries`
- `TopologicalRaycasts`
- `DamageAliasUtil`
- `EntityPacketUtil`
- `packet-policies.md`

## Proposed Design

Add a `TopologicalExplosions` helper under `globe.world.topology`.

The helper should provide narrowly-scoped operations that can be called from
mixins around `ServerExplosion` internals:

- `canonicalAffectedBlocks(level, center, rawToBlow)`
- `affectedEntities(level, sourceEntity, center, radius)`
- `entityView(level, center, entity)` returning canonical entity, nearest alias
  position/box, distance, and knockback vector
- `getSeenPercent(level, center, entityView, damageCalculator)` or an adapter
  that maps vanilla exposure samples into the visible frame before clipping

Prefer wrapping vanilla calculations at the points where raw position enters
distance, AABB, and clip logic. If the vanilla implementation makes that too
fragile, copy the minimal block/entity calculation into `TopologicalExplosions`
and keep comments tied to the source anchors.

## Concrete Implementation Plan

### 1. Add `TopologicalExplosions`

Create `mod-fabric/src/main/java/globe/world/topology/TopologicalExplosions.java`.

Public API:

- `static List<BlockPos> canonicalAffectedBlocks(ServerLevel level, Vec3 center, List<BlockPos> rawTargets)`
- `static boolean hurtEntities(ServerExplosion explosion, ServerLevel level, Entity source, DamageSource damageSource, ExplosionDamageCalculator damageCalculator, Vec3 center, float radius, Map<Player, Vec3> hitPlayers)`
- `static float seenPercent(ServerLevel level, Vec3 visibleCenter, Entity entity, AABB visibleEntityBox)`
- `static EntityView entityView(ServerLevel level, Vec3 center, Entity entity)`

Helper records:

- `EntityView(Entity entity, AABB canonicalBox, AABB visibleBox, Vec3 visibleOrigin, Vec3 direction, double normalizedDistance)`

Implementation details:

- Early-return `false` from `hurtEntities(...)` when topology is disabled so the
  vanilla private method can continue.
- Build the vanilla blast AABB around the visible explosion center and gather
  entities with `TopologicalEntityQueries.entities(level, source, aabb)`.
- For each real entity, map its canonical box into the nearest alias frame to
  the explosion center.
- Compute distance from the visible alias origin to the visible explosion
  center. Use TNT position for `PrimedTnt`; otherwise use eye position, matching
  vanilla.
- Reimplement the vanilla damage formula with wrapped distance:

```text
doubleRadius = radius * 2
dist = visibleDistance / doubleRadius
power = (1 - dist) * exposure
damage = ((power * power + power) / 2 * 7 * doubleRadius + 1)
```

- Still call `damageCalculator.shouldDamageEntity(...)` and
  `damageCalculator.getKnockbackMultiplier(...)`.
- For non-vanilla custom `ExplosionDamageCalculator` subclasses, document the
  compatibility choice before implementation:
  - preferred: use the vanilla topological formula for damage amount so seam
    behavior is correct;
  - fallback: call the custom calculator only when topology is disabled, because
    vanilla's method reads raw `entity.distanceToSqr(center)`.
- Apply knockback to the real entity using the visible-frame direction.
- Store player knockback in `hitPlayers` exactly as vanilla does.
- Redirectable projectile ownership and `entity.onExplosionHit(source)` stay
  vanilla.

Risk mitigations:

- Do not alter `ServerLevel.explode(...)` packet behavior beyond the existing
  packet virtualization.
- Do not globally patch `ExplosionDamageCalculator`.
- Keep entity handling as a copied, source-anchored version of
  `ServerExplosion.hurtEntities()`, because stacking wraps around distance,
  exposure, damage amount, and direction would be more brittle.
- Keep block handling as a return-value canonicalization of vanilla ray output
  so blast ray resistance and stopping behavior remain vanilla-shaped.

### 2. Add `ServerExplosionMixin`

Create `mod-fabric/src/main/java/globe/world/mixin/ServerExplosionMixin.java`.

Shadows:

- `ServerLevel level`
- `Vec3 center`
- `Entity source`
- `float radius`
- `DamageSource damageSource`
- `ExplosionDamageCalculator damageCalculator`
- `Map<Player, Vec3> hitPlayers`

Hooks:

- `@Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)`
  - replace the return value with
    `TopologicalExplosions.canonicalAffectedBlocks(level, center, cir.getReturnValue())`
    when topology is enabled.
- `@Inject(method = "hurtEntities", at = @At("HEAD"), cancellable = true)`
  - call `TopologicalExplosions.hurtEntities(...)`;
  - cancel only when it returns `true`.

Register in `globe-world.mixins.json`.

### 3. Block Dedupe Implementation

`canonicalAffectedBlocks(...)` should:

- use `TopologyContext.canonicalBlock(...)` for every raw target;
- preserve first-seen order with a `LinkedHashMap<BlockPos, BlockPos>` or
  `LinkedHashSet<BlockPos>`;
- return canonical positions only;
- count raw target count, canonical target count, and duplicate count for
  optional diagnostics.

Keep `interactWithBlocks(...)` and `createFire(...)` vanilla. They will receive
canonical target positions through the injected return value.

### 4. Exposure Implementation

`seenPercent(...)` should copy the sampling grid from
`ServerExplosion.getSeenPercent(...)`, but sample the visible alias box instead
of `entity.getBoundingBox()`.

For each sample:

- clip from visible sample point to visible center;
- use `ClipContext.Block.COLLIDER` and `ClipContext.Fluid.NONE`;
- pass the real entity as collision context;
- rely on existing chunk/block lookup canonicalization during the clip.

If clipping against alias endpoints exposes a missed block-identity issue, add a
small `TopologicalRaycasts` overload for entity-sample exposure instead of
duplicating more ray code in the explosion helper.

### 5. Diagnostics And Regression

Add an `EXPLOSIONS` diagnostics channel only if manual testing needs it.

Useful fields:

- center raw/canonical;
- raw target count;
- canonical target count;
- duplicate block count;
- entity id;
- visible alias origin;
- normalized distance;
- exposure;
- damage;
- knockback.

## Block Damage

Block ray output should be treated as visible-frame candidate positions. Before
destruction:

- canonicalize each candidate `BlockPos`;
- dedupe by canonical `BlockPos`;
- preserve deterministic ordering, ideally by first visible hit distance or
  first vanilla insertion order;
- pass canonical positions to state lookup, resistance, drops, and destruction.

Acceptance criteria:

- A blast straddling a seam does not drop or destroy the same canonical block
  twice.
- A blast centered near an edge destroys the same canonical block set as an
  equivalent interior control.

## Entity Damage And Knockback

Entity search should use `TopologicalEntityQueries` over the visible blast AABB.
For each candidate:

- map the canonical entity box into the nearest alias to the explosion center;
- compute distance/falloff from that alias;
- run exposure from alias sample points to the visible explosion center;
- apply damage to the real entity;
- apply knockback vector in visible-frame direction, then store vanilla motion on
  the real entity.

Acceptance criteria:

- Entities just across an X seam, Z seam, and corner seam take appropriate
  damage and knockback.
- Players receive knockback direction matching the visible blast.
- Entities raw-near but visually far through canonical wrapping are not
  over-damaged by an alias explosion frame.

## Implementation Phases

### Phase 1: Mixin Feasibility Spike

Inspect `ServerExplosion` constructor and methods to decide whether
`@WrapOperation` hooks are stable enough for:

- `Level.getEntities(...)`;
- `Entity.distanceToSqr(...)` or vector distance calculations;
- `Explosion.getSeenPercent(...)`;
- `Set<BlockPos>` insertion/destruction iteration.

Record the chosen hook points in this plan before implementation if the method
shape is surprising.

### Phase 2: Entity Path

Implement entity query, wrapped distance, exposure, and knockback first. This
has the largest player-visible impact and can reuse existing query/raycast
primitives.

### Phase 3: Block Dedupe

Canonical-dedupe `toBlow` before block interaction. Add diagnostics if duplicate
candidate counts are useful during testing.

### Phase 4: Regression Matrix

Compare seam and interior blasts for:

- TNT;
- creeper;
- bed/respawn-anchor explosion where applicable;
- explosion near X seam, Z seam, and corner seam;
- entities at multiple heights;
- water/solid block exposure differences.

## Documentation Updates When Implemented

- Add explosion behavior to `docs/mod-mechanics/entities.md`,
  `docs/mod-mechanics/blocks-and-ticks.md`, or a new combat/world-events
  section depending on final size.
- Update `docs/mod-mechanics/packet-policies.md` only if packet behavior changes.
- Shrink the explosion section in
  `minecraft-coordinate-coverage-audit.md`.
