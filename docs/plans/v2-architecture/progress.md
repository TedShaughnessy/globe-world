# V2 Progress Tracker

Last updated: 2026-06-10.

This page is a short status map for the v2 architecture notes. "Fully done"
means the primitive is implemented and its durable behavior is documented in
`docs/mod-mechanics/`. Worldgen remains separate because the explicit
`GenerationWindow` primitive has not been built.

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
| Actor-local entity targets | `ActorLocalTargetView` / `ActorLocalTargets` own actor-local alias positions, boxes, distances, line of sight, path targets, nearest-target selection, and actor-range query adapters. The old AI alias adapter has been retired. | [Entities](../../mod-mechanics/entities.md) |
| Broad entity-query replacement | `TopologicalEntityQueries` gathers visible-frame entity boxes through canonical tile-edge splits, identity dedupe, and alias-frame players. Server entity lookup, brain sensors, pickup, container-open checks, splash-potion candidates, and ray sweeps use it. | [Entities](../../mod-mechanics/entities.md), [Topology](../../mod-mechanics/topology.md) |
| Migration strategy | The non-worldgen phases have landed: topology contexts, packet policies, diagnostics, settings split, actor-local targets, broad entity queries, topological raycasts, and v1 AI adapter retirement. | [Migration Strategy](migration-strategy.md) |
| Seam-behavior checklist | [Seam-Behavior Checklist](seam-test-matrix.md) captures manual checks and automation candidates for preserving seam behavior during ongoing polish. | [Seam-Behavior Checklist](seam-test-matrix.md) |

## Remaining

| Area | Notes |
| --- | --- |
| `GenerationWindow` / full toroidal worldgen window | V1 spillover and structure handling still exist, but the explicit v2 generation-window primitive has not been built. |

## Next Sensible Pieces

1. Design `GenerationWindow` separately before touching worldgen ownership
   code.
2. Keep client-side projectile prediction, long-ray behavior, and raycast
   diagnostics on the seam checklist as polish/regression checks.
3. Convert the most deterministic seam checks into automated tests or focused
   diagnostics before changing worldgen ownership.
