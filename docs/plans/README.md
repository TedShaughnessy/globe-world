# Plans

This folder tracks proposed or investigative work that is not yet fully
implemented.

Implemented investigations have been folded into
[Globe World Mod Mechanics](../mod-mechanics/README.md), which is the entry
point for current behavior and modded code anchors.

## Future Improvements

- [Worldgen improvements](worldgen-improvements.md): standalone plan for
  replacing implicit spillover and structure-edge behavior with an explicit
  toroidal generation window.
- [Seam-behavior checklist](seam-behavior-checklist.md): manual regression
  matrix and automation candidates for preserving seam behavior during future
  work.

## Implemented From Plans

- The first v2 low-risk pass is implemented and folded into
  [Globe World Mod Mechanics](../mod-mechanics/README.md): topology context,
  packet policies, diagnostics channels, actor-local target views, and the
  split settings schema.
- The topological raycast primitive is implemented and folded into
  [Topology](../mod-mechanics/topology.md#topological-raycast-primitives):
  block clips, entity sweeps, line of sight, arrow-family hits, shared
  server-side projectile movement, view-vector rays, and attack-range sweeps.
- The v2 migration is implemented for topology contexts, packets, diagnostics,
  settings, entity targeting, broad entity queries, and raycasts. Worldgen
  improvement work is tracked separately in
  [Worldgen improvements](worldgen-improvements.md).
  The completed v2 feature-plan pages have been retired; durable behavior now
  lives in mod mechanics docs.
