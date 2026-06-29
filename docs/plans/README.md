# Plans

This folder tracks proposed or investigative work that is not yet fully
implemented.

Implemented investigations have been folded into
[Globe World Mod Mechanics](../mod-mechanics/README.md), which is the entry
point for current behavior and modded code anchors.

## Future Improvements

- [AI social alias follow-ups](ai-social-alias-followups.md): remaining audited
  social follow, temptation, short-range movement, and brain/social behaviors
  that still need actor-local alias handling after the owner-adjacent tameable
  fixes.
- [Hexagonal tiles](hexagonal-tiles.md): post-MVP plan for manual/Atlas
  regression coverage and later edge-blend terrain continuity.
- [Seamless hexagonal edge blending](hexagonal-edge-blending.md):
  implemented straight-sided continuous Voronoi blending plus remaining
  discrete-worldgen and six-seam acceptance work.
- [Large-tile Atlas biome projection follow-ups](large-tile-atlas-biome-projection.md):
  remaining UX and palette questions after implementing survey-mode
  biome-colored projections for large tiles.
- [Minecraft 26.2 upgrade plan](minecraft-26-2-upgrade.md): dependency bump,
  Loom source regeneration, mixin audit, packet policy refresh, and runtime
  validation plan for the next Minecraft target.
- [Seam-behavior checklist](seam-behavior-checklist.md): manual regression
  matrix and automation candidates for preserving seam behavior during future
  work.

## Implemented / Retired Notes

- [Offset square tiling](offset-square-tiling.md): implemented even-width
  Overworld topology with alternating half-offset columns, coupled-lattice
  aliases, edge-blended terrain, local-time invariance, Atlas integration, and
  remaining manual/discrete-worldgen acceptance work.
- [Hexagonal tiles first run](hexagonal-tiles-first-run.md): implemented as the
  experimental runtime topology slice; durable behavior lives in
  [Topology](../mod-mechanics/topology.md), [Chunks](../mod-mechanics/chunks.md),
  and [Worldgen](../mod-mechanics/worldgen.md). Seamless hex generation and
  structure continuity remain in the broader
  [Hexagonal tiles](hexagonal-tiles.md) plan.
