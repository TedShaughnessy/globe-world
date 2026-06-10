# Plans

This folder tracks proposed or investigative work that is not yet fully
implemented.

Implemented investigations have been folded into
[Globe World Mod Mechanics](../mod-mechanics/README.md), which is the entry
point for current behavior and modded code anchors.

## Future Architecture

- [V2 architecture plan](v2-architecture/README.md): rebuild-oriented plan for
  keeping the same canonical-world concept while giving coordinate frames,
  topology access, packets, entities, raycasts, worldgen, configuration, and
  migration their own stronger boundaries.

## Implemented From Plans

- The first v2 low-risk pass is implemented and folded into
  [Globe World Mod Mechanics](../mod-mechanics/README.md): topology context,
  packet policies, diagnostics channels, actor-local target views, and the
  split settings schema.
- The topological raycast primitive is implemented and folded into
  [Topology](../mod-mechanics/topology.md#topological-raycast-primitives):
  block clips, entity sweeps, line of sight, arrow-family hits, shared
  server-side projectile movement, view-vector rays, and attack-range sweeps.
- The non-worldgen v2 migration is implemented; the remaining v2 architecture
  work is the explicit
  [GenerationWindow](v2-architecture/worldgen-window.md) worldgen model.
