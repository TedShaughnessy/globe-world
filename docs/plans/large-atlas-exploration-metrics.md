# Large Atlas Exploration Metrics

## Problem

Full Atlas discovery works for small and medium tiles, but it becomes the wrong
promise for very large worlds. At `512` chunks, the current `512x512` Atlas
texture already reaches the largest vanilla map density of `16` blocks per
pixel. Beyond that, a whole-tile torus map becomes an overview at best, and on
Earth-scale presets each pixel covers tens of thousands of blocks.

For large tiles, stop treating full-tile cartography as the main progression
loop. The Atlas should become a survey instrument: it rewards meaningful travel,
biome variety, and a useful network of placed Atlases.

## Proposed Cutoff

- `<= 512` chunks: keep the current full-map Atlas projection and completion
  model.
- `> 512` chunks: do not attempt a literal whole-tile map projection from the
  placed object or held item. Show a survey-style projection instead.
- Future local-map work can still add high-fidelity player-local pages, but
  that should be separate from full-tile completion.

## MVP Metrics

Keep the first large-world metric set small:

- **Biomes visited**: track unique Overworld biomes visited by non-spectator
  players in the canonical tile. Vanilla does not store this as a `Stats`
  counter; the `adventure/adventuring_time` advancement tracks it as one
  `minecraft:location` criterion per required biome id, triggered from
  `ServerPlayer` every second. Globe can read or mirror those completed
  criterion names, but should still store its own shared world-level biome set
  because Atlas reward progress is shared across players.
- **Atlas coverage**: measure the canonical area encompassed by active powered
  Atlases. Approximate this with a coarse wrapped grid instead of exact circle
  union math. Count a cell covered if any in-budget Atlas radius reaches its
  center.
- **Atlas network size**: count loaded or saved powered Atlases with travel
  enabled. This makes teleport progression depend on building a useful network,
  not coloring every map pixel.

Optional later metric:

- **Distance traveled**: existing Minecraft custom stats such as walking,
  sprinting, swimming, boating, riding, and flying distance can add flavor, but
  should not be the first balance anchor because they are easy to grind in one
  region.

## Reward Direction

For large tiles, replace full-completion travel gating with survey milestones:

- Early travel unlock: enough biomes visited plus at least two travel-enabled
  Atlases.
- Better travel/network budget: more biome variety and more covered cells.
- Radius and effect points: continue scaling from meaningful absolute
  exploration, but use coverage and biome count instead of full-tile percent.
- Mastered Atlas: keep the full-completion achievement only for tiles where
  full projection remains enabled, or introduce a separate large-world
  "Surveyed Atlas" advancement.

## UI And Projection

When a large tile disables literal map projection:

- Placed Atlas hologram renders a survey torus: visited-biome bands, coverage
  arcs, travel nodes, and linked-node paths.
- Held Atlas renders a compact local survey: nearby travel nodes, biome count,
  coverage progress, and available destinations.
- The power screen replaces full-discovery percent emphasis with biome count,
  coverage cells, travel-node count, available points, and travel unlock state.

## Implementation Sketch

1. Add a large-tile mode check near the Atlas projection sizing helper.
2. Add shared saved state for visited biome ids and coarse covered-cell
   progress, likely adjacent to the existing Atlas discovery/reward state.
3. For biome visits, prefer importing completed criteria from vanilla's
   `minecraft:adventure/adventuring_time` advancement when the advancement is
   present. Also sample the player's current canonical Overworld biome during
   the existing Atlas tracker cadence so shared Atlas progress updates even
   before the vanilla advancement UI is opened or completed.
4. Recompute coarse Atlas coverage from saved powered Atlas entries and their
   in-budget radii.
5. Change `GlobeDiscoveryRewards` so large tiles derive points, caps, and travel
   unlocks from biome count, coverage cells, and travel-network size.
6. Update the Atlas UI payload and screen labels for large-tile survey mode.
7. Replace object/item full-map rendering with the survey projection when the
   tile is above the cutoff.

## Open Questions

- Should the cutoff be exactly `512` chunks, or should `513-8192` chunks keep a
  coarse overview mode while only bigger tiles switch fully to survey mode?
- Should biome milestones count raw biome ids, biome tags/categories, or both?
- Should coverage include all powered Atlases, or only travel-enabled Atlases?
- Should "Surveyed Atlas" be per-world shared progress only, or should players
  also get individual credit for contributing biomes?
