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
- Add topological raycast prototypes behind unused or debug-only paths.
- Add diagnostics channels without changing gameplay behavior. Implemented; see
  [Client diagnostics](../../mod-mechanics/client.md#local-sky-and-diagnostics).

## Phase 3: Migrate Low-Risk Systems

- Start with packet virtualization organization and diagnostics. Implemented;
  see [Packet Policies](../../mod-mechanics/packet-policies.md).
- Move block/chunk alias helper calls to the topology access layer.
- Keep behavior equivalent and update docs as files move.
- Use [Low-Risk Implementation Plan](low-risk-implementation-plan.md) for the
  current status of completed primitives and remaining split-settings work.

## Phase 4: Migrate High-Risk Systems

- Broad entity queries; the first `ActorLocalTargetView` facade is implemented
  and documented in [Entities](../../mod-mechanics/entities.md).
- Projectile and raycast behavior.
- Worldgen generation window and spillover ownership.

Each high-risk migration should have its own test matrix and rollback point.

## Phase 5: Retire V1 Adapters

- Remove duplicate wrapping utilities once all callers use the v2 layer.
- Retire stale mixins that only existed to patch local frame mismatches.
- Do not keep compatibility shims for v1 saved config/world data unless that
  becomes an explicit product requirement again.

## Compatibility Notes

- V2 does not need backward compatibility with v1 worlds or saved settings.
- Prefer a clean schema and clear startup failure over silent reinterpretation
  of old topology, packet, entity, or worldgen ownership data.
- If compatibility becomes useful later, add it as a separate migration tool or
  importer instead of shaping the core v2 architecture around it.

## Open Questions

- Should v2 ship as a separate mod id, a major version of the same mod, or a
  long-lived branch until migration is complete?
- Which behavior needs automated tests before refactoring begins?
- Which old-world rejection message or optional importer would be clearest for
  users if v2 changes saved settings or generation policy?
