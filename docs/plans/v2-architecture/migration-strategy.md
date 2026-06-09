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

- Add coordinate-frame naming and `TopologyContext`.
- Add topology access helpers while keeping existing v1 utilities alive.
- Add topological raycast prototypes behind unused or debug-only paths.
- Add diagnostics channels without changing gameplay behavior.

## Phase 3: Migrate Low-Risk Systems

- Start with packet virtualization organization and diagnostics.
- Move block/chunk alias helper calls to the topology access layer.
- Keep behavior equivalent and update docs as files move.

## Phase 4: Migrate High-Risk Systems

- Entity queries and AI target views.
- Projectile and raycast behavior.
- Worldgen generation window and spillover ownership.

Each high-risk migration should have its own test matrix and rollback point.

## Phase 5: Retire V1 Adapters

- Remove duplicate wrapping utilities once all callers use the v2 layer.
- Retire stale mixins that only existed to patch local frame mismatches.
- Keep compatibility shims only for saved config/world data that needs them.

## Compatibility Notes

- Existing v1 worlds should continue to load when practical.
- If topology config changes are not backward-compatible, provide an explicit
  migration path or reject with a clear error.
- Do not silently reinterpret existing worldgen ownership.

## Open Questions

- Should v2 ship as a separate mod id, a major version of the same mod, or a
  long-lived branch until migration is complete?
- Which behavior needs automated tests before refactoring begins?
- How much v1 compatibility is worth preserving if the v2 topology model changes
  saved settings or generation policy?
