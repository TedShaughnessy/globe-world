# Topological Raycasts

## Problem

V1 has several ray-like systems: block picking, entity picking, AI line of
sight, projectile entity hits, fishing line behavior, and projectile movement.
Some are alias-aware, some are partially wrapped, and some remain known v1
limitations.

Projectile block clipping was the clearest example: a launch vector could be
correct while the later collision path still used raw vanilla space. The shared
move-vector path and the remaining shared server `ProjectileUtil` ray helpers
now use topological hit results.

## V2 Direction

Make topological raycasting and swept collision a first-class primitive. A
caller should ask the topology layer to trace from one frame to another and get
back both the canonical hit identity and the visible hit frame.

## Implemented Primitive

The first shared primitive now lives in
`mod-fabric/src/main/java/globe/world/topology/TopologicalRaycasts.java`.
Durable behavior is documented in
[Topology](../../mod-mechanics/topology.md#topological-raycast-primitives).

Current entry points:

- `topologicalClip(...)`: visible-frame block/fluid clip with canonicalized
  block hit identity.
- `topologicalEntitySweep(...)`: canonical entity identity tested through
  visible alias hitboxes, with the earliest visible hit per entity.
- `topologicalLineOfSight(...)`: actor-local target visibility.
- `topologicalViewVector(...)`: server-side view-vector block/entity hits.
- `topologicalHitEntitiesAlong(...)`: server-side attack-range entity sweeps.
- `topologicalProjectileMove(...)`: combined block/entity projectile movement.

Existing v1 behavior now enters the primitive for alias line of sight,
arrow-family entity alias hits, arrow-family block clipping, and the
server-authoritative shared `ProjectileUtil.getHitResultOnMoveVector(...)` path
used by thrown projectiles, fishing bobbers, fireworks, shulker bullets, llama
spit, fireballs, and wind charges. The 2026-06-10 projectile/raycast audit also
covered `ProjectileUtil.getHitResultOnViewVector(...)` and
`ProjectileUtil.getHitEntitiesAlong(...)`, which are used by brush validation
and attack-range component weapons.

## Requirements

- Support block hits and entity hits.
- Return canonical block/entity identity for server authority.
- Preserve a virtual hit position for visuals, particles, sounds, and knockback
  direction where needed.
- Support actor-local rays, viewer-local picking, and projectile movement.
- Define how many tile aliases a ray may scan so tiny tiles do not explode cost.
- Preserve vanilla clip modes for blocks and fluids.

## Implementation Sketch

Create shared ray primitives:

```text
topologicalClip(level, from, to, options)
topologicalEntitySweep(source, from, to, searchBox, predicate)
topologicalLineOfSight(actor, target)
topologicalProjectileMove(projectile, nextPosition)
```

Internally, the raycast can test the nearest relevant alias frames, convert
hits back to canonical identity, and choose the earliest hit along the visible
segment.

## Expected Benefits

- One place to reason about seam-crossing projectile behavior.
- Shared behavior between client picking and server validation.
- Less duplicated line-of-sight logic for mobs and special attacks.
- Easier test matrix for wrapped blocks, wrapped entities, and obstructed seam
  cases.

## Audit Result

The server-authoritative projectile/raycast primitive is implemented. Vanilla
projectile classes that collide through the shared move-vector helper are
covered by `ProjectileUtilTopologicalMoveMixin`; arrows and tridents are covered
by `AbstractArrowAliasCollisionMixin`; splash-potion area effects keep their
separate wrapped entity query.

Remaining items are polish and regression checks, not v2 blockers:

- Client-side projectile prediction still uses vanilla raw helpers. The server
  remains authoritative, but seam-crossing projectiles should be watched for
  visible correction snaps.
- Very long rays still use the default nearest-alias entity radius unless a
  caller opts into `EntitySweepOptions.withAliasTileRadius(...)`.
- A `/globeworld raycast` diagnostic command would make future seam bug reports
  easier to inspect, but is optional.
