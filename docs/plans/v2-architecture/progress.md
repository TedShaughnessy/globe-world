# V2 Progress Tracker

Last updated: 2026-06-10.

This page is a short status map for the v2 architecture notes. "Fully done"
means the primitive is implemented and its durable behavior is documented in
`docs/mod-mechanics/`. "Partial" means the first v2 shape exists, but planned
migrations or audits remain. "Not started" means there is no dedicated v2
primitive yet beyond v1 behavior and planning notes.

## Fully Done

| Area | What is done | Durable docs |
| --- | --- | --- |
| Coordinate-frame naming | `TopologyContext` / `TopologyContexts` name canonical, virtual, viewer-facing, and wrapped-distance conversions while still returning vanilla types. | [Topology](../../mod-mechanics/topology.md) |
| Packet policy inventory | `PacketVirtualizationPolicies` records the packet policy table while handwritten packet copies preserve packet-specific semantics. | [Packet Policies](../../mod-mechanics/packet-policies.md) |
| Diagnostics channels | `/globeworld debug` controls session-only diagnostic channels; investigation logs are quiet by default. | [Client](../../mod-mechanics/client.md#local-sky-and-diagnostics) |
| Settings split | `GlobeSettings` is the saved/network schema with topology, presentation, and gameplay sections. Old flat `TilingSettings` data is not imported. | [Topology](../../mod-mechanics/topology.md), [Client](../../mod-mechanics/client.md#packet-and-cache-model) |
| Low-risk block/chunk helper migration | Runtime chunk lookup, block mutation/access, ticks, packet fanout, chunk alias visibility, and related helpers now use `TopologyContext` names at subsystem boundaries. | [Topology](../../mod-mechanics/topology.md), [Chunks](../../mod-mechanics/chunks.md), [Blocks And Ticks](../../mod-mechanics/blocks-and-ticks.md) |
| Entity and waypoint packet helper migration | Entity packet aliases, waypoint block/chunk aliases, wrapped waypoint range checks, and waypoint chunk visibility use topology-context frame helpers. | [Entities](../../mod-mechanics/entities.md) |
| Topological raycasts and projectile authority | `TopologicalRaycasts` provides block clips, entity sweeps, line of sight, view-vector rays, attack-range sweeps, and projectile movement. Arrow-family paths, the shared server projectile move-vector path, and shared server `ProjectileUtil` ray helpers route through the primitive. | [Topology](../../mod-mechanics/topology.md), [Entities](../../mod-mechanics/entities.md) |

## Partial

| Area | Done so far | Remaining |
| --- | --- | --- |
| Topology access layer | The low-risk access helpers are in place and several runtime systems call them. | Decide which remaining utilities should move behind the layer; keep server authority, presentation, and alias visibility boundaries clear. |
| Actor-local entity targets | `ActorLocalTargetView` / `ActorLocalTargets` package existing AI alias behavior and are used by debug commands, nearest-entity selection, actor-range queries, and look-at goals. | Broader AI/range/pathing callers can migrate as nearby behavior is touched; wider raw `EntityGetter` replacement is still a design risk. |
| Broad entity-query replacement | `TopologicalEntityQueries` gathers visible-frame entity boxes through canonical tile-edge splits, identity dedupe, and alias-frame players. `ServerEntityGetter`, brain sensors, pickup, container-open checks, splash-potion candidates, and ray sweeps use it. | Audit remaining raw `EntityGetter` callers, especially collision-style queries and places where visible-frame boxes would affect vanilla side effects. |
| Migration strategy | Phases 2 and 3 have substantial implemented pieces; phase 4 has primitives for entity targets, broad entity queries, and raycasts. | Worldgen window work, visual/diagnostic polish, and eventual v1 adapter retirement still need separate passes. |
| Seam-behavior checklist | [Seam-Behavior Checklist](seam-test-matrix.md) now captures manual checks and automation candidates for preserving v1 behavior during v2 migration. | Convert the crispest checks into automated tests or command diagnostics as the harness matures. |

## Not Started

| Area | Notes |
| --- | --- |
| `GenerationWindow` / full toroidal worldgen window | V1 spillover and structure handling still exist, but the explicit v2 generation-window primitive has not been built. |
| V1 adapter retirement | `CoordUtil`, `AiAliasUtil`, and targeted mixins remain valid backing utilities/adapters until their callers are migrated deliberately. |

## Next Sensible Pieces

1. Keep migrating nearby callers to `TopologyContext` and `ActorLocalTargets`
   opportunistically.
2. Keep client-side projectile prediction, long-ray behavior, and raycast
   diagnostics on the seam checklist as polish/regression checks.
3. Design `GenerationWindow` separately before touching worldgen ownership
   code.
4. Convert the most deterministic seam checks into automated tests or focused
   diagnostics before retiring any v1 adapters.
