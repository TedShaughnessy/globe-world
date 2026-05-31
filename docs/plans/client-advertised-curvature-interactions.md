# Client-Advertised Curvature Interactions

## Goal

Allow curvature to become a per-client visual preference while keeping block,
fluid, and item interactions aligned with what that client sees.

Today, curvature is safest as a saved world/server setting because interaction
raycasts are recomputed server-side using the same curvature that clients render.
If curvature becomes client-local, the server needs a bounded way to know which
visual curve a given interaction used.

## Current Behavior

- `GlobeCurvatureShader` bends rendered terrain, lines, clouds, entities, and
  particles from the active saved curvature setting.
- `GlobeCurvedRaycast` traces segmented block clips through the inverse curve.
- `LocalPlayerMixin` uses that curved ray for ordinary client block targeting.
- `ItemMixin` redirects `Item.getPlayerPOVHitResult(...)` so server-side item
  validation for buckets, boats, bottles, spawn eggs on fluids, and
  place-on-water items uses the same world/server curvature.

This avoids client/server disagreement as long as the server and client share
the same curvature setting.

## Problem

A purely local client curvature setting would make server-side item raycasts use
the wrong curve. The client could see a bucket or boat target under one visual
curve while the server validates against another. That can cause misses,
different hit faces, wrong fluid pickup, or placement at a different position.

## Candidate Designs

### Separate Curvature State Event

The client sends its current interaction curvature to the server whenever the
setting changes, after joining a world, and after changing dimension. The server
stores the sanitized value on the `ServerPlayer` and uses it when
`GlobeCurvedRaycast` is called for that player's interaction.

Pros:

- Low packet overhead.
- Server has a stable per-player value for all vanilla item paths.
- Works with existing `ItemMixin` because the server can read player-attached
  curvature state.

Cons:

- State can become stale if a packet is dropped or if settings change during an
  interaction.
- The server must decide fallback behavior before the first client state packet
  arrives.

### Send Curvature With Each Interaction

The client sends the curvature value as part of, or just before, each affected
interaction. The server uses that value only for the matching interaction and
then discards it.

Pros:

- The server validates against the exact value the client meant for that use.
- Less persistent per-player state.

Cons:

- Vanilla interaction packets do not have extra fields, so this likely needs a
  small custom packet that is correlated with the following vanilla interaction.
- Ordering and replay edge cases are more complex.
- More network chatter during repeated item use or block breaking.

## Preferred First Version

Use the separate curvature state event.

Implementation sketch:

1. Add a tiny client-to-server custom payload containing dimension and curvature
   percent.
2. Send it on login/world join, dimension change, and whenever the local
   curvature UI changes.
3. Store sanitized per-dimension curvature on `ServerPlayer` through a mixin
   interface or attachment.
4. Make `GlobeCurvature` accept an optional per-player override for interaction
   raycasts.
5. Keep world/server curvature as the fallback when no client value is known.
6. Clamp accepted values to the UI-supported range and ignore values for
   non-tiled dimensions.
7. Add debug output showing world curvature and player-advertised interaction
   curvature separately.

## Validation Rules

- The server must keep normal reach/range checks.
- Clamp curvature percent to `TilingSettings.sanitizeCurvaturePercent(...)`.
- Only apply the override in dimensions where Globe tiling is enabled.
- Rate-limit or coalesce client curvature updates.
- Consider logging repeated changes while an interaction is in progress.
- Never trust client-provided block positions solely because they match the
  advertised curvature; server block/fluid state remains authoritative.

## Open Questions

- Should a multiplayer server be able to force world curvature for all players?
- Should client-advertised curvature be disabled by default on dedicated
  servers?
- Is per-dimension state enough, or do we need to record the exact value used
  for the next interaction packet?
- Should block breaking use client-advertised curvature too, or only item POV
  interactions that recompute raycasts server-side?

## Success Criteria

- Players can choose local curvature without bucket/boat/water targeting drift.
- Vanilla server validation still controls reach, permissions, collision, and
  final block/fluid/entity state.
- Missing or invalid client curvature state falls back to current
  server-authoritative behavior.
- Debugging can clearly show whether an interaction used server curvature or a
  client-advertised override.
