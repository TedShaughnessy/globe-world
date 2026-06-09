# Entities And AI

## Problem

V1 stores non-player entities canonically and virtualizes packets per viewer.
That is the correct ownership model. The fragile part is AI and combat: vanilla
often starts from raw entity positions, raw AABBs, or custom target searches.
V1 therefore needs many targeted fixes for goals, sensors, look controls,
pathing, melee, ranged launch vectors, and special attacks.

## V2 Direction

Make actor-relative target information a shared concept. Instead of each goal
asking for `target.getX()` and then remembering to alias it, entity queries
should be able to return a local target view:

```text
realEntity
canonicalPosition
actorLocalPosition
actorLocalAabb
wrappedDistance
aliasLineOfSight
```

Vanilla hooks still need adapters, but those adapters should consume one shared
target model.

## Requirements

- Entity identity remains canonical and vanilla-compatible.
- Player entities may still live in raw alias coordinates during active play.
- Non-player mounted stacks remain canonical as a stack.
- Actor-local target views must preserve vanilla Y behavior unless explicitly
  changed.
- Query boxes must be able to include wrapped-near entities that raw AABBs miss.
- Path requests can use alias targets without claiming that vanilla pathfinding
  is fully toroidal.

## Implementation Sketch

Add a topology-aware entity query service:

- `nearestTarget(actor, candidates)`
- `targetsInActorRange(actor, rawBox, predicate)`
- `actorLocalView(actor, target)`
- `pathTargets(actor, target)`

Then gradually migrate AI mixins to call the service rather than doing
per-class wrapping math.

## Expected Benefits

- Fewer per-mob special cases.
- More consistent behavior between targeting, looking, pathing, reach, and
  attack launch.
- Better diagnostics: `/globeworld entity` can report the same target view the
  AI consumed.

## Open Questions

- How far should v2 go toward replacing raw vanilla `EntityGetter` behavior?
- Should target views be immutable records created per query, or cached briefly
  per mob tick?
- Which custom mobs remain better served by small targeted mixins?
