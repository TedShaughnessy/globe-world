# Hexagonal Tiles

Status: the experimental runtime topology MVP is implemented. Chunk/block
ownership, alias loading and packets, mutations, core entity/query helpers,
game events, explosions, and bounded worldgen ownership use `TileGeometry`.
The first-run scope is preserved in
[Hexagonal tiles first run](hexagonal-tiles-first-run.md).

Topology diagnostics, the Atlas lattice projection, the residual non-worldgen
gameplay audit, and seamless continuous base-terrain blending are implemented.
Remaining work is broader Atlas regression coverage plus the six-seam manual
acceptance matrix for discrete features, carvers, structures, and worldgen side
effects.

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

## Current Geometry Decision

The implemented mask has narrow tips at the left and right when X is drawn
horizontally and Z vertically, matching the original east/west orientation.
This orientation and its translation-invariant ownership rule are geometry
revision `hex-east-west-v2`:

- saved `tile_size` remains the approximate tip-to-tip width;
- horizontal lattice spacing is three quarters of that width;
- lattice `A = (horizontal spacing, half height)` gives one oblique cycle;
- lattice `B = (0, height)` gives a north/south cycle with no longitude change;
- `A-B` gives the other oblique cycle;
- local solar time uses horizontal lattice spacing as its X period, so all
  aliases and every north/south line agree;
- debug labels and tests name the six translations `+A`, `-A`, `+B`, `-B`,
  `+(A-B)`, and `-(A-B)`.

Exact nearest-center ties use lexicographic lattice-coordinate order, which is
preserved when both candidates are translated by the same `(k,l)`. Viewer
presentation uses a separate origin-preferring nearest-alias tie. Orientation,
tie-breaking, size normalization, lattice vectors, and chunk-mask ownership
are save contracts rather than incidental implementation details.

Experimental `hex-east-west-v1` canonical worlds are not migrated. Back them up
and create a new v2 world; the old settings did not persist enough revision
information to detect and safely retain v1 ownership. Persisted Atlas and
survey layouts do include the projection identity and are rejected/recreated
when the geometry revision changes.

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

The fixed left/right-pointed orientation satisfies the "left and right points"
design constraint while still allowing the Atlas to expose a rectangular torus
in hex-lattice coordinates.

## Topology Model

`TileGeometry`, `SquareTileGeometry`, `HexTileGeometry`, and the
`TopologyContext` delegation boundary expose geometry-neutral descriptions for:

- a geometry/projection revision suitable for persistent-data compatibility;
- canonical chunk count and canonical block area;
- canonical chunk bounds and iteration over the canonical chunk mask;
- lattice basis vectors in chunks and blocks;
- the integer lattice coordinate `(k, l)` and translation vector taking a raw
  position to its canonical owner;
- exposed chunk-edge boundary segments, grouped into the three paired seam
  relations;
- normalized torus coordinates and their inverse, as described in
  [Atlas Projection](#atlas-projection).

Square geometry implements the same descriptive API with its ordinary
orthogonal basis. Runtime callers avoid casting to `HexTileGeometry`; shared
coupled-lattice query decomposition and visual-alias coefficient bounds live in
`LatticeMath`.

## Runtime Systems

Status: the residual non-worldgen square-helper audit is implemented. Whole
positions are canonicalized and virtualized through `TileGeometry` rather than
by choosing X and Z aliases independently. The completed groups were:

1. Atlas discovery, power state, effect radii, travel, survey state/windows,
   held rendering, and projector rendering.
2. World spawn search/fallbacks, respawn helpers, portal placement, lodestones,
   compasses, fishing-line endpoints, filled-map marker aliases, and other
   player-facing targets.
3. Natural-spawn and player-distance helpers that still used one-axis wrapping.
4. Client debug HUD, tile border renderer, world-spawn marker, and any
   presentation code that derives a square tile index.
5. Commands that implemented canonicalization, border distance, or alias
   selection directly with `CoordUtil`.

The gameplay slice includes geometry-area spawn search, paired natural-spawn
candidate canonicalization, Nether portal POI loading/search across seams,
hex-safe End fallback placement, compass targets, fishing pull and rendered
line endpoints, all filled-map decoration classes, banner validation, and sign
facing. Remaining `CoordUtil` scalar uses are either explicit scalar policies
(such as longitude) or deferred worldgen seed/noise/structure work below.

Worldgen seed/noise/structure call sites are tracked separately below. A
mechanical `CoordUtil.wrap*`, `virtual*`, and `wrappedDistance*` search should be
kept as an audit tool, but each match must be classified: a genuine scalar-axis
policy is different from an accidental split of a two-dimensional identity
operation.

## Debug Tile Visuals And Test Commands

Diagnostics should be the first post-MVP implementation slice. They make every
later Atlas and gameplay test cheaper.

Status: the initial slice is implemented. Geometry now exposes its revision,
lattice basis/coordinates/translations, and exact boundary segments. Commands
and the HUD report that shared data, and `F3+Y` renders the exact hex staircase,
the camera tile's neighbor ring, paired seam colors/labels, and the
geometry-correct world-spawn alias. The optional selected-chunk ownership
arrow/fill mode remains a later diagnostic enhancement.

### Client Overlay

Replace the square-only `GlobeTileBorderRenderer` path in hex mode with a
geometry-driven overlay:

- draw the exact chunk-composed canonical boundary, including its staircase
  edges, at player height;
- draw the nearest ring of six alias boundaries from lattice translations;
- color opposite seams as pairs and label them `±A`, `±B`, and `±(A-B)`;
- optionally fill canonical chunks faintly or mark chunk centers so ownership
  and tie-break boundaries are visible;
- virtualize the world-spawn marker with `nearestAlias(...)`;
- keep the existing square renderer unchanged through the same descriptive
  geometry API.

An ideal second debug mode shows a selected raw chunk, its canonical owner, its
`(k, l)` lattice coordinate, and the translation between them. This is more
useful for diagnosing packet/query bugs than a decorative continuous hex
outline.

### Commands And HUD

Make the existing commands topology-aware:

- `/globeworld pos` reports raw and canonical positions/chunks, lattice
  coordinate `(k, l)`, translation vector, geometry revision, and whether the
  chunk is in the canonical mask;
- replace square tile-X/tile-Z alias reporting in hex mode with `(k, l)`;
- make `/globeworld teleport_canon` use `TopologyContext.canonicalBlock(...)`;
- make `/globeworld teleport_alias <k> <l>` apply the geometry basis;
- add a seam form of the border teleport command that accepts one of the six
  named seam directions and an inset, while retaining the square command
  behavior;
- report distance to the actual discrete boundary, not to the rectangular
  bounds around the mask;
- make the F3/debug HUD show the same canonical owner, lattice coordinate, and
  nearest seam information as the server command.

Add deterministic seam test locations for all six edge directions and the
mask's vertex/tie regions. The command should keep Y when safe and clearly
report the raw and canonical destination.

## Atlas Projection

Status: the initial Atlas integration is implemented. `AtlasTorusProjection`
uses the geometry's A/B basis while preserving square X/Z output. Literal
discovery/sampling/refresh, held local views, survey state/windows/markers,
canonical area rewards, Atlas power radii, and travel now use whole
two-dimensional geometry operations. Map and survey saves persist tiling mode
and a projection identity containing the geometry revision and lattice basis;
incompatible layouts reset with a warning, while legacy square layouts remain
accepted.

The Atlas remains a torus. Its texture is a rectangular parameterization of
the quotient, not a literal rectangular X/Z crop of the canonical chunk mask.

The projection boundary derived from `TileGeometry` provides:

- canonical/raw block and chunk position to normalized `(u, v)`;
- normalized `(u, v)` or Atlas pixel to one deterministic canonical sample
  position;
- world-space local-window samples to Atlas UV;
- wrapped UV/cell deltas for markers and reveal windows;
- canonical chunk count, block area, and a stable projection identity.

For the implemented hex lattice, use the `A/B` basis. Conceptually, with
`A = (horizontal spacing, half height)` and `B = (0, height)`, solve
`position = u*A + v*B`, wrap `u` and `v` modulo one, and canonicalize the
inverse representative through `TileGeometry`. This makes all translations by
`A`, `B`, and `A-B` land on the same Atlas coordinate. Define pixel-center and
boundary tie rules in unit tests rather than relying on floating-point accident.

Square mode should use the same projection API and preserve its current output.
The client torus mesh can continue using rectangular UVs; the changed meaning
of the texture supplies the oblique hex-lattice projection.

### Atlas Data And Behavior Migration

Move these systems onto the projection:

- literal-map discovery and reveal-radius checks;
- pixel-to-world sampling and refresh-on-block-change;
- completion percentage and discovered physical area;
- player and Atlas markers;
- held player-centered windows;
- placed torus textures;
- survey visited-chunk canonicalization, windows, and markers;
- travel-node discovery checks;
- reward thresholds that currently assume `tileSize * tileSize`;
- Atlas power canonical positions and wrapped effect/travel distance.

Local held/survey windows should remain world-readable: sample a player-centered
X/Z window, then project each sample through the lattice mapping. Do not treat a
rectangular crop of the UV texture as a rectangular crop of world X/Z in hex
mode.

The hex canonical area is the lattice determinant (`width * height` chunks for
the current basis), not necessarily `tileSize * tileSize`. Survey cutoffs,
travel targets, area rewards, and progress text must use canonical chunk
count/area.

### Save Compatibility

`GlobeMapSavedData` and `GlobeAtlasSurveyState` currently identify their layout
primarily by tile size. That is insufficient once square and hex worlds of the
same configured width have different projections.

Persist at least:

- tiling mode;
- geometry/projection revision;
- normalized size and lattice dimensions;
- texture resolution where applicable.

On mismatch, either perform an explicit projection migration or create fresh
discovery data with a visible log message. Never silently interpret square
pixels as hex UVs. Atlas power entries keyed by canonical block position also
need a deliberate mode/orientation migration policy if runtime topology changes
are supported.

### Remaining Atlas Tests

Current unit coverage proves square axis preservation, A/B translation
invariance, deterministic pixel-center round trips, and determinant area. Add
broader tests proving:

- every canonical chunk maps exactly once into a discrete lattice-domain test
  grid;
- reveal radii and refreshes cross all six seams;
- held windows and markers select the viewer-nearest copy;
- completion area and survey travel targets use canonical area;
- existing square save matching remains unchanged;
- square and hex saves with the same configured width cannot be confused.

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

The detailed terrain design and implementation sequence lives in
[Seamless hexagonal edge blending](hexagonal-edge-blending.md).

Status: implemented for continuous base-terrain inputs. The terrain sampler
computes block-space seam bands against the ideal
continuous Voronoi hex, not the exact chunk staircase. Inside the safe
interior, it samples vanilla terrain unchanged. In an edge band, it blends
translated vanilla samples using lattice-invariant weights. Near vertices, all
relevant translated copies converge symmetrically.

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

## Other Product Integration

### Local Solar Time

Status: implemented through geometry-aware longitude helpers. The east/west
orientation makes X a valid longitude: `B` preserves X, while `A` and `A-B`
change X by one horizontal lattice period. Sky, lightmap, sleep/spawn gates,
clocks, debug output, and other local-time users therefore agree at every alias
and remain constant north to south.

### Settings And Explanations

- Keep hex visibly experimental until the non-generation acceptance matrix
  passes.
- Describe `tile_size` as normalized approximate width and also show the
  resulting width, height, and canonical chunk count.
- Explain that continuous base terrain is seamless while discrete worldgen
  stages remain experimental pending the acceptance matrix.
- Keep Nether hex disabled unless it is promoted into a separate tested
  milestone.
- If topology can be changed after world creation, warn when a change would
  invalidate Atlas projection data or canonical ownership.

### Performance

Before calling the non-generation integration complete, profile:

- alias enumeration on minimum-size tiles at large view distance;
- broad entity/query splitting near vertices;
- the debug overlay with multiple alias outlines;
- literal Atlas resampling and held-window updates;
- survey windows on heavily explored saves;
- many players or Atlases clustered across several seam aliases.

## Configuration And Compatibility

- `HEX` is saved through the existing topology settings without changing square
  save semantics.
- Hex tile sizes are normalized separately from square sizes.
- Hex Overworld terrain resolves to `TerrainMode.EDGE_BLEND`.
- Nether hex is currently disabled; do not imply otherwise in commands or UI.
- Existing square worlds remain valid and keep their terrain mode.
- Commands and UI should make it clear that hex mode is experimental until the
  manual seam matrix passes.

## Suggested Milestones

Completed MVP:

1. Define the discrete mask, lattice vectors, size normalization, canonical
   mapping, and geometry tests.
2. Introduce `TileGeometry` and route core `TopologyContext` operations through
   it.
3. Implement runtime chunk ownership, packet aliases, mutations, and the main
   entity/query/event/explosion paths.

Next, outside seamless generation:

1. ~~Freeze orientation and add the geometry descriptor/revision contract.~~
2. ~~Implement truthful tile visuals, lattice-aware commands, and HUD output.~~
3. ~~Add the shared square/hex Atlas projection with property tests.~~
4. ~~Version Atlas/survey saved data and migrate literal discovery, sampling,
   refresh, markers, held windows, and placed torus rendering.~~
5. ~~Migrate survey mode, Atlas rewards, powers, and travel to geometry-aware
   canonicalization, area, and distance.~~
6. ~~Audit the remaining non-worldgen square-helper call sites: spawn/respawn,
   portals, lodestones/compasses, fishing, filled maps, spawning/distance, and
   client presentation.~~
7. ~~Orient the hex east/west and make local solar time alias-invariant along
   north/south lines.~~
8. Run the six-seam non-generation acceptance matrix and profile tiny tiles.

Later generation track:

1. Complete hex-aware positional randomness, feature/carver/structure
   ownership, and spillover audits.
2. Implement the
   [six-edge/corner terrain blend](hexagonal-edge-blending.md).
3. Audit structures and forced-progression structures near every seam.
4. Consider Nether hex only after Overworld behavior is stable.

## Test Matrix

Minimum manual validation for the non-generation integration milestone:

- debug boundary, alias labels, commands, and HUD agree at every seam;
- block placement and breaking across all six visible edges;
- chunk load/unload and full chunk packet aliases across edges and at corners;
- player walking, teleporting, sleeping, respawning, and portal travel near
  edges;
- entity tracking, melee, projectiles, explosions, and item pickup across each
  edge direction;
- scheduled ticks, random ticks, fluids, light updates, and block entities near
  edge pairs;
- Atlas discovery, held projection, placed torus, travel-node display, and
  refresh-on-block-change in hex mode;
- Atlas save/reload, mode mismatch handling, and multiplayer shared discovery;
- local solar time is identical at all aliases of the same canonical position;
- lodestones, compasses, fishing lines, filled-map markers, and world-spawn
  markers choose the nearest visible alias;
- minimum-size tile stress with high view distance and several players;
- Nether remains square/disabled and portal travel from a hex Overworld is
  coherent;
- End dimension remains untiled.

Later generation acceptance adds:

- terrain continuity across all six edges and each corner region;
- trees, ores, decorations, carvers, and structures crossing every seam;
- deterministic generation and matching biome/surface/cave results at all
  lattice-equivalent positions.

## Open Questions

- Whether the debug overlay should default to the exact staircase boundary or
  show a simplified continuous hex in addition to the exact boundary.
- Whether existing Atlas discovery should reset or be explicitly migrated when
  topology/projection identity changes.
- What exact hex size parameter the UI should eventually expose: approximate
  width (current behavior), radius, edge length, or canonical chunk count.
- How much non-rectangular canonical chunk storage can vanilla chunk-distance
  and simulation-distance logic tolerate before caps need hex-specific rules?
- Whether the Atlas should optionally annotate the hex Voronoi domain and its
  paired seams over the rectangular lattice UV texture.
