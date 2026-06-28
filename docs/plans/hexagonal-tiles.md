# Hexagonal Tiles

## Goal

Add an optional hexagonal wrapped topology where the canonical world tile is a
hexagon made from whole Minecraft chunks. The hex tile should repeat across the
plane, store mutable state only once in canonical chunks, and still support the
Atlas as a toroidal projection.

For terrain continuity, the first supported hex mode will use edge blend only.
Hex mode will not try to support compact torus or true periodic lattice terrain
until the topology, atlas projection, and seam behavior are proven.

## Non-Goals

- Do not replace square tiling or change existing square-world saves.
- Do not support arbitrary polygonal tiles.
- Do not split ownership inside a chunk. Hex boundaries must be built from
  whole chunks.
- Do not preserve exact vanilla terrain near hex edges. Edge blend may distort
  terrain in seam bands as long as opposite edges converge cleanly.
- Do not implement from-scratch toroidal pathfinding as part of the first hex
  milestone.

## Shape Requirements

The canonical hex is a discrete chunk mask, not a continuous mathematical
polygon applied at block precision. The mask must satisfy:

- paired opposite sides have equal chunk-edge spans;
- the left and right points are two chunks wide so visible tiling does not hinge
  on a single tip chunk;
- every raw chunk maps to exactly one canonical owner chunk;
- every canonical owner chunk is reachable from the raw plane by lattice
  translations;
- neighboring hex copies use two independent translation vectors, with the
  third edge-pair translation derived from those vectors;
- block-level canonicalization preserves whole-chunk ownership by canonicalizing
  the containing chunk first, then applying the same chunk translation to the
  block-local coordinates.

The first implementation should choose one orientation and keep it fixed. A
left/right-pointed hex is the natural fit for the "left and right points"
design constraint, while still allowing the Atlas to expose a rectangular torus
in hex-lattice coordinates.

## Topology Model

The current topology code wraps X and Z independently. Hex mode needs a 2D tile
geometry abstraction so callers ask geometry questions instead of composing
one-axis helpers.

Introduce a small geometry boundary, for example `TileGeometry`, with square
and hex implementations. It should provide:

- `canonicalChunk(rawX, rawZ)`;
- `canonicalBlock(rawX, y, rawZ)`;
- `isCanonicalChunk(x, z)` and `isCanonicalBlock(pos)`;
- `aliasVector(raw, canonical)`;
- `nearestAlias(canonical, viewer)`;
- `wrappedDelta(a, b)` and wrapped distance helpers;
- `canonicalQueryBoxes(visibleBox)` or an equivalent chunk/entity query splitter;
- atlas projection helpers from canonical block/chunk coordinates to torus UV.

`TopologyContext` should delegate to this geometry instead of calling
`CoordUtil.wrapChunk(...)`, `CoordUtil.wrapBlock(...)`, and
`CoordUtil.virtualChunk(...)` axis by axis. `CoordUtil` can keep square-friendly
compatibility helpers, but hex-aware code should use 2D methods.

## Runtime Systems

After the geometry boundary exists, update runtime paths that currently assume
rectangular independent periods:

1. Chunk lookup and packet relabeling: canonical chunk ownership, alias tickets,
   loaded-alias tracking, full chunk packets, unload packets, block updates,
   biome resends, and light updates.
2. Player and entity placement: login, respawn, teleport canonicalization,
   entity storage canonicalization, passenger positioning, and viewer-nearest
   packet aliases.
3. Entity and block queries: wrapped distance, broad-phase entity query boxes,
   raycast target frames, projectile sweeps, collision checks, explosions,
   sensors, and interaction reach.
4. Ticks and mutations: block updates, scheduled ticks, random ticks, fluids,
   POI access, block entities, and alias mutation guards.
5. Diagnostics: `/globeworld pos`, border-distance commands, teleport-to-edge
   commands, alias reporting, and seam testing helpers need hex terms instead
   of square border terms.

The broad entity-query splitter is a specific risk: square mode currently
splits a visible `AABB` into independent X and Z canonical intervals. Hex mode
needs either chunk-mask based splitting or a small set of shifted query boxes
covering all lattice aliases touched by the visible box.

## Worldgen Plan

Hex mode supports only edge blend terrain initially.

### Required Worldgen Changes

- Worldgen region reads and writes must canonicalize through the hex geometry.
- `GenerationWindow` must resolve raw chunk requests to the nearest cache alias
  using the hex lattice, not independent X/Z wrapping.
- Spillover writes must classify the canonical destination chunk through the
  hex geometry and preserve the existing guarded replay semantics.
- Positional random factories must hash equivalent hex-lattice positions to the
  same random stream where the coordinate unit is known.
- Carver, feature, surface, biome, and structure seed hooks must use canonical
  hex positions or lattice-equivalent positions.
- Structure reference generation must store enough alias-shift metadata to
  place pieces crossing any of the six hex edges.

### Edge Blend Terrain

The terrain sampler should compute seam bands against the six hex edges. Inside
the safe interior, sample vanilla-like terrain at the canonical coordinate. In
an edge band, blend the base sample with the translated sample from the paired
opposite edge. Near corners, blend all relevant translated copies so the three
meeting edge-pair relations converge to the same result.

The edge blend contract is:

- opposite hex edges produce the same terrain, biome, surface, and cave samples
  at corresponding points;
- corner regions are deterministic and do not leave cracks where three
  translated neighborhoods meet;
- blend width is measured in blocks and capped similarly to square edge blend;
- the blend method is deterministic from world seed, dimension, hex size, and
  canonical/lattice coordinates;
- the terrain-mode UI and saved settings reject compact torus and periodic
  lattice for hex mode, falling back to edge blend.

### Explicitly Deferred Terrain Work

- True oblique periodic lattice noise.
- Compact torus noise for hex worlds.
- Exact vanilla-scale preservation at all hex corners.
- External worldgen-provider support beyond the current conservative behavior
  for synthetic regions.

## Atlas Plan

The Atlas can remain a toroidal projection because a translated hex tile with
paired opposite sides is still topologically a torus. The implementation should
not treat the Atlas texture as literal square X/Z space in hex mode.

Add a projection layer:

- canonical block/chunk to lattice UV;
- lattice UV to representative canonical block/chunk for sampling;
- canonical block to Atlas pixel;
- Atlas pixel to canonical sample position;
- wrapped distance on the Atlas for reveal radii and travel-node cells.

The client torus mesh can continue to render rectangular UVs. In hex mode those
UVs represent the parallelogram lattice domain whose opposite sides identify
the hex edge pairs. Unknown pixels, survey-mode textures, held Atlas windows,
travel-node markers, and refresh-on-block-change should all use the projection
layer instead of direct `x/z -> pixel` math.

## Configuration And Compatibility

- Add `HEX` to `TilingMode`.
- Save hex mode through the existing topology settings without changing square
  save semantics.
- Sanitize hex tile sizes separately from square tile sizes. Hex sizes may need
  stronger constraints than "even chunk count" to preserve the two-chunk tips
  and paired edge spans.
- In hex mode, force `TerrainMode.EDGE_BLEND` for Overworld and Nether wrapped
  dimensions.
- Existing square worlds remain valid and keep their terrain mode.
- Commands and UI should make it clear that hex mode is experimental until the
  manual seam matrix passes.

## Suggested Milestones

1. Define the discrete hex chunk mask, lattice vectors, size constraints, and
   canonical mapping rules. Add pure unit tests for chunk/block canonicalization,
   alias vectors, nearest aliases, and wrapped distances.
2. Introduce the tile-geometry abstraction and migrate `TopologyContext` to it
   while keeping square behavior byte-for-byte equivalent where practical.
3. Implement hex runtime chunk ownership and packet aliasing behind the new
   mode. Validate block placement, chunk loading, and player canonicalization.
4. Update entity queries, raycasts, explosions, ticking, and mutation guards to
   use geometry-aware wrapped distance and alias selection.
5. Implement hex-aware `GenerationWindow`, spillover classification, and
   positional random wrapping.
6. Implement hex edge blend terrain and test all six edges plus six corners on
   small tiles.
7. Add the Atlas projection layer and switch literal-map discovery, survey
   cells, held rendering, and travel-node cells to geometry-aware UVs.
8. Audit structures and forced progression structures near all hex edges.
9. Add user-facing settings, diagnostics, and manual seam checklist entries.

## Test Matrix

Minimum manual validation before calling hex mode playable:

- block placement and breaking across all six visible edges;
- chunk load/unload and full chunk packet aliases across edges and at corners;
- player walking, teleporting, sleeping, respawning, and portal travel near
  edges;
- entity tracking, melee, projectiles, explosions, and item pickup across each
  edge direction;
- scheduled ticks, random ticks, fluids, light updates, and block entities near
  edge pairs;
- terrain continuity across all six edges and each corner region;
- trees, ores, decorations, carvers, and structures crossing edges;
- Atlas discovery, held projection, placed torus, travel-node display, and
  refresh-on-block-change in hex mode;
- Nether hex mode with its own tile size and portal scale;
- End dimension remains untiled.

## Open Questions

- What exact hex size parameter should the UI expose: radius in chunks, edge
  length in chunks, width in chunks, or approximate area?
- Should Nether hex mode be allowed immediately, or should Overworld hex mode
  ship first?
- Should local solar time follow lattice U, world X, or a configurable
  longitude axis in hex mode?
- How much non-rectangular canonical chunk storage can vanilla chunk-distance
  and simulation-distance logic tolerate before caps need hex-specific rules?
- Should the first Atlas view show the parallelogram lattice domain directly,
  or mask/annotate the hex fundamental domain on the texture?
