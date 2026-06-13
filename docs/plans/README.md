# Plans

This folder tracks proposed or investigative work that is not yet fully
implemented.

Implemented investigations have been folded into
[Globe World Mod Mechanics](../mod-mechanics/README.md), which is the entry
point for current behavior and modded code anchors.

## Future Improvements

- [Minecraft coordinate coverage audit](minecraft-coordinate-coverage-audit.md):
  investigation matrix for vanilla coordinate systems, current Globe World
  coverage, and remaining coordinate-index/geometry risks.
- [Topological POI and village queries](topological-poi-and-village-queries.md):
  plan for POI search, villager bed/job discovery, bees, raids, and
  lightning-rod targeting across tile seams.
- [Topological game events and vibrations](topological-game-events-and-vibrations.md):
  plan for sculk sensors, shriekers, wardens, allays, and other game-event
  listeners across tile seams.
- [Topological explosions](topological-explosions.md): plan for server-side
  explosion block dedupe, entity damage, knockback, and exposure across tile
  seams.
- [Entity query and collision resolution](entity-query-and-collision-resolution.md):
  plan for classifying raw entity query callers, block-trigger hooks, and the
  first physical collision policy.
- [Worldgen direct mutation and structure persistence audit](worldgen-direct-mutation-and-structure-persistence-audit.md):
  plan for remaining direct chunk/section mutation, unobserved spillover side
  effects, and structure query/persistence checks.
- [Seam-behavior checklist](seam-behavior-checklist.md): manual regression
  matrix and automation candidates for preserving seam behavior during future
  work.
