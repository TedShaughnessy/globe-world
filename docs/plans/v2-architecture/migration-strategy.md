# Migration Strategy

## Goal

V2 should not interrupt v1 feature completion. The safest rebuild path is to
extract concepts behind new APIs first, then migrate subsystems one at a time
while preserving behavior.

## Phase 1: Freeze V1 Behavior

- Finish the intended v1 feature set.
- Update mod mechanics docs so implemented behavior is durable.
- Move unresolved work into plans or explicit non-goals.
- Add focused manual or automated tests for seam behavior that v2 must
  preserve.

## Phase 2: Add Internal Primitives

- Add coordinate-frame naming and `TopologyContext`. Implemented; durable
  behavior now lives in [Topology](../../mod-mechanics/topology.md).
- Add topology access helpers while keeping existing v1 utilities alive.
- Add topological raycast primitives behind unused or low-risk adapter paths.
  Implemented for alias line of sight, arrow-family entity/block hits, and the
  shared server-side projectile move-vector path; durable behavior now lives in
  [Topology](../../mod-mechanics/topology.md#topological-raycast-primitives)
  and [Entities](../../mod-mechanics/entities.md#ai-interaction-and-pathing).
- Add diagnostics channels without changing gameplay behavior. Implemented; see
  [Client diagnostics](../../mod-mechanics/client.md#local-sky-and-diagnostics).

## Phase 3: Migrate Low-Risk Systems

- Start with packet virtualization organization and diagnostics. Implemented;
  see [Packet Policies](../../mod-mechanics/packet-policies.md).
- Move block/chunk alias helper calls to the topology access layer.
  Implemented for runtime block/chunk helper paths; see
  [Topology](../../mod-mechanics/topology.md),
  [Chunks](../../mod-mechanics/chunks.md), and
  [Blocks And Ticks](../../mod-mechanics/blocks-and-ticks.md).
- Keep behavior equivalent and update docs as files move.
- Use [Low-Risk Implementation Plan](low-risk-implementation-plan.md) for the
  current status of completed primitives and remaining split-settings work.

## Phase 4: Migrate High-Risk Systems

- Broad entity queries; the first `ActorLocalTargetView` facade is implemented
  and documented in [Entities](../../mod-mechanics/entities.md).
- Projectile and raycast authority has an implemented shared primitive. Client
  projectile prediction visuals, very long rays, and optional raycast
  diagnostics remain polish/regression work rather than migration blockers.
- Worldgen generation window and spillover ownership.

Each high-risk migration should have its own test matrix and rollback point.

## Phase 5: Retire V1 Adapters

- Remove duplicate wrapping utilities once all callers use the v2 layer.
- Retire stale mixins that only existed to patch local frame mismatches.
- Do not keep compatibility shims for v1 saved config/world data unless that
  becomes an explicit product requirement again.

## Compatibility Notes

- The split settings migration intentionally does not import worlds whose
  `globe_world` field still has the old `TilingSettings` shape. The
  `TilingSettings` adapter has been removed; the saved-settings holder is
  `GlobeSettingsHolder`. Create-world controls edit topology through
  `TopologySettings`, curvature through `PresentationSettings`, and day/night
  behavior through `GameplaySettings`.
