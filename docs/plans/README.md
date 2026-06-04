# Plans

This folder holds proposed or investigative work. When a plan is implemented,
move its durable mechanics into [../mod-mechanics/README.md](../mod-mechanics/README.md)
and keep only historical investigation here if it is still useful.

## Active Plans

1. [Client-Advertised Curvature Interactions](client-advertised-curvature-interactions.md):
   design for making curvature a per-client visual preference while keeping
   block, fluid, bucket, and boat interactions aligned with the client view.
2. [MakeUp Ultra Fast Curvature Compatibility](makeup-ultra-fast-curvature-compatibility.md):
   plan for packaging a Globe-compatible MakeUp shader pack, exposing Globe
   curvature values to Iris, and keeping the shader patch stack auditable.

## Completed Plans

1. [Effective Render And Simulation Distance Caps](effective-distance-caps.md):
   implemented shared effective-distance policy, client render caps, and
   per-dimension server simulation caps without changing saved global settings.
2. [Mob Tracking Across Tile Borders](mob-tracking-across-tile-borders-plan.md):
   implemented nearest-alias mob sensing, target retention, entity path targets,
   look direction, melee reach, and ranged-goal distance checks. Full toroidal
   pathfinding and projectile physics remain deferred.
3. [Tiling Context And Dimension Key Plan](tiling-context-and-dimension-key-plan.md):
   implemented reliability pass for scoped tiling context and dimension-key
   equality checks.
4. [Seamless Wrapping Plan](seamless-wrapping-plan.md): implemented terrain
   generation direction.
5. [Terrain Periodicity Investigation](terrain-periodicity-investigation.md):
   completed investigation of compact torus, edge blend, and periodic lattice
   terrain modes.
6. [Chunk Alias Tracker Lifecycle Plan](chunk-alias-tracker-lifecycle-plan.md):
   implemented dimension-scoped alias tracking and lifecycle cleanup.
7. [Worldgen Spillover Lifecycle Plan](worldgen-spillover-lifecycle-plan.md):
   implemented server-lifecycle-scoped spillover queues, cleanup, and
   diagnostics.
8. [Light Update Alias Fanout Plan](light-update-alias-fanout-plan.md):
   implemented incremental light update fanout for loaded aliases.
9. [Packet Virtualization Audit Plan](packet-virtualization-audit-plan.md):
   completed the Minecraft 26.1.2 position-bearing packet audit. High and
   medium priority packets are implemented or explicitly classified; durable
   behavior now lives in the mod mechanics and vanilla packet inventory docs.
10. [Entity Canonical Storage Plan](entity-canonical-storage-plan.md):
   implemented non-player canonical entity storage, mounted-stack
   canonicalization, player-passenger visible-tile positioning,
   player-controlled vehicle packet translation, and diagnostics. Manual
   validation cases remain listed in the plan.
11. [Entity Visual Alias Rendering](entity-visual-alias-rendering-plan.md):
   implemented client-only visual aliases for non-player, not-leashed entities
   including non-player mounted stacks, alias-aware client picking, debug
   toggles/diagnostics, and snap-on-rebase behavior. Remaining edge cases live
   in the entity mechanics open audits.

## Optional Optimizations

1. [Client Canonical Chunk Cache](client-canonical-chunk-cache.md): optional
   client-side alias warm-start cache proposal; defer until the server packet
   and lifecycle behavior is stable.
