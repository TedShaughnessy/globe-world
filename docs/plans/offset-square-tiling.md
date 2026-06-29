# Offset Square Tiling

Status: implemented; manual acceptance and discrete-worldgen validation remain.

## Goal

Add an optional Overworld topology made from axis-aligned square tiles whose
east/west neighbors are offset north/south by half a tile. The canonical tile
continues to own one `W x W` square of whole chunks, while every other square
is a virtual translated view of that state.

The first implementation should:

- alternate aligned and half-offset columns;
- preserve a pure north/south lattice translation so scrolling local time
  behaves like hex mode;
- support even tile widths only;
- use edge-blended terrain only;
- preserve the established square and hex modes;
- reuse the existing geometry, lattice, Atlas, and topology-aware gameplay
  boundaries instead of adding offset-specific behavior to each subsystem.

## Non-Goals

- Do not support odd tile widths. An exact alternating half-tile offset must be
  a whole number of chunks, and the two east/west seam transforms agree only
  when the offset is exactly half the tile period.
- Do not support arbitrary offset amounts.
- Do not support compact-torus or periodic-lattice terrain in this mode.
- Do not enable offset-square Nether topology in the first implementation.
- Do not create a second canonical tile, mirror alternating columns, split
  chunks, or assemble alias chunks from block-level slices.
- Do not change the existing `SQUARE` or `HEX` save contracts or terrain
  output.
- Do not add a new placed-projector mesh for small tiles.

## Geometry Contract

### Saved Mode And Revision

Add a saved tiling mode:

```text
OFFSET_SQUARE = "offset_square"
```

Use geometry revision:

```text
offset-square-north-south-v1
```

The revision fixes the orientation, basis, canonical interval, boundary
tie-breaking, and half-offset direction. Any later change to those decisions
requires a new revision.

### Size And Canonical Ownership

Let `W` be the sanitized tile width in chunks:

- `W >= 2`;
- `W` is even;
- odd configured values round up to the next even number, matching existing
  square-size normalization;
- `H = W / 2`;
- the canonical chunk interval on both axes is `[-H, H)`;
- the canonical tile contains exactly `W * W` chunks;
- block ownership follows containing-chunk ownership, so every canonical
  boundary remains chunk-aligned.

The canonical tile is the same axis-aligned square shape as ordinary square
mode. Only its alias translations differ.

### Lattice Basis

With X drawn east/west and Z drawn north/south, use:

```text
A = (W, H)
B = (0, W)
A - B = (W, -H)
```

`A` moves one column east and half a tile south. `A-B` moves one column east
and half a tile north. `B` is a pure north/south tile translation. Repeated
columns therefore alternate between aligned and half-offset positions because:

```text
2A = (2W, W) = (2W, 0) modulo B
```

The six edge-touching neighboring tiles are:

```text
+A, -A, +B, -B, +(A-B), -(A-B)
```

The existing lattice seam labels and three seam-pair colors can represent
these neighbors without a new coordinate vocabulary.

### Canonicalization

Canonicalization should use direct integer arithmetic rather than a nearest
center search. For a raw chunk `(rawX, rawZ)`:

```text
k = floorDiv(rawX + H, W)
canonicalX = rawX - k * W

shiftedZ = rawZ - k * H
l = floorDiv(shiftedZ + H, W)
canonicalZ = shiftedZ - l * W
```

The raw-to-canonical translation is:

```text
k*A + l*B = (k*W, k*H + l*W)
```

Required properties:

- canonical chunks map to themselves;
- translating a chunk by `A`, `B`, or `A-B` preserves its canonical owner;
- `latticeCoordinate(raw)` returns the exact `(k, l)` used above;
- block canonicalization canonicalizes the containing chunk and applies the
  same whole-chunk translation to the block coordinates;
- floating-point positions preserve their block-local displacement under the
  same lattice translation;
- tie-breaking at exact boundaries is the half-open canonical interval
  `[-H, H)`.

### Boundaries And Nearest Aliases

North and south are one complete edge each and lead through `-B` and `+B`.
East and west are split into two half-edges:

- one east half reaches `+A`;
- the other east half reaches `+(A-B)`;
- the corresponding west halves reach `-A` and `-(A-B)`.

Expose exact block-aligned `BoundarySegment` entries with those outside alias
coordinates. The midpoint where each east/west edge changes neighbor is a
T-junction and needs deterministic tests.

Nearest aliases, wrapped distances, canonical query boxes, and nearby alias
boxes must operate on complete X/Z positions. A bounded search around the
analytically estimated `(k, l)` is acceptable and matches the proven hex
approach.

### Longitude

The east/west lattice period is `W` chunks:

- `A` and `A-B` change X by exactly `W`;
- `B` does not change X;
- every alias therefore has the same `X mod W` longitude;
- north/south movement does not change local solar time.

No local-time formula change should be required, but add explicit translation
invariance tests for all three seam relations.

## Implementation Plan

### 1. Add The Mode And Configuration Policy

Update:

- `globe.world.config.TilingMode`;
- `globe.world.config.TopologySettings`;
- `globe.world.util.DimensionTiling`;
- `globe.world.client.GlobeWorldSettingsControls`.

Required policy:

- include `OFFSET_SQUARE` in enabled Overworld modes;
- display it as `Offset Square (Experimental)`;
- explain the north/south half-offset and even-width requirement in its
  tooltip;
- sanitize its size with the existing even square-size rule;
- force or resolve its terrain mode to `EDGE_BLEND`;
- disable terrain-mode selection while it is active, as for hex;
- include it in Overworld missing-stronghold and water-only-seed defaults;
- keep `netherEnabled()` restricted to ordinary `SQUARE`;
- leave simple world presets on ordinary square mode unless a later product
  decision adds an offset preset.

Use an exhaustive switch in `TileGeometry.create(...)` rather than treating
every non-hex mode as square.

### 2. Implement `OffsetSquareTileGeometry`

Add:

```text
mod-fabric/src/main/java/globe/world/topology/OffsetSquareTileGeometry.java
```

Implement the full `TileGeometry` contract:

- saved revision and lattice basis;
- direct chunk and block canonicalization;
- canonical membership;
- lattice-coordinate recovery;
- six neighboring tile coordinates;
- six split boundary descriptions;
- nearest chunk, block, vector, and box aliases;
- canonical and viewer-facing boxes;
- wrapped block/chunk distances;
- canonical broad-query boxes;
- nearby alias-box enumeration;
- `LatticeBlendGeometry` exposure;
- canonical chunk count of `W * W`.

Prefer clear offset-square formulas over copying the hex chunk-mask
construction. Extract a small geometry-neutral lattice helper only where both
implementations genuinely share nearest-translation or alias-enumeration logic.

### 3. Remove Remaining “Hex Versus Square” Assumptions

Audit the current direct `TilingMode.HEX` branches in:

- `CoordUtil`;
- `GlobeEntityAliasing`;
- `PeriodicNoiseUtil`;
- `GlobeTileBorderRenderer`;
- `GlobeWorldSettingsControls`;
- `TileGeometry`;
- `TopologySettings`;
- `DimensionTiling`.

Classify behavior by capability instead:

- independent-axis square geometry;
- coupled two-dimensional lattice geometry;
- geometry with continuous lattice edge blending.

Whole-position operations in `CoordUtil` should delegate to `TileGeometry`
for every enabled mode where practical. Keep scalar helpers only for callers
whose operation is genuinely one-dimensional, such as longitude. Do not let
offset-square callers independently choose X and Z aliases.

Rename hex-specific generic code where appropriate:

- `visualHexOffsets` to a lattice-neutral alias enumerator;
- `drawHexTiles`/`drawHexBoundary` to lattice-boundary terminology;
- `sampleHexEdgeBlendedPlane` to a lattice edge-blend name;
- diagnostics that say “hex blend” when they are reporting shared lattice
  blending.

Hex behavior and output must remain unchanged after this refactor.

### 4. Runtime Alias And Gameplay Integration

Most runtime systems already consume `TopologyContext` and `TileGeometry`.
Verify rather than duplicate logic for:

- chunk canonicalization and alias tickets;
- chunk packet relabeling;
- block and light packet fanout;
- mutation routing;
- entity tracking and visual aliases;
- topological entity/collision queries;
- POI and village queries;
- explosions and game events;
- raycasts and projectiles;
- path targets and wrapped distances;
- player login, respawn, wake-up, and explicit canonical rebases;
- compass, lodestone, map marker, fishing-line, and other viewer-nearest
  targets;
- portal lookup and placement in an offset-square Overworld.

`GlobeEntityAliasing` should enumerate aliases from the geometry basis without
casting to `HexTileGeometry`. Whole-tile rebase detection should compare raw
movement with the residual between canonical positions for any coupled lattice
mode.

Add focused runtime tests or fixtures for the east/west half-edge split. A
query spanning the midpoint T-junction must see both relevant neighbor frames
without duplicate entity identity.

### 5. Edge-Blended World Generation

Offset-square terrain always uses the shared lattice blend with:

```text
A = (W, W/2)
B = (0, W)
```

The ideal continuous blend cell is the Voronoi cell of this lattice. It is
hexagonal even though discrete ownership is the configured square. This is
intentional: the blended field is globally invariant under `A` and `B`, so it
agrees across every square ownership cut without moving the blend band onto
the T-junction staircase.

Generalize `PeriodicNoiseUtil` dispatch so both hex and offset-square geometry
use the physical-X/Z lattice blend path while ordinary square modes retain
their current samplers bit-for-bit.

Verify:

- density, climate, cave, aquifer, surface, and provider noise uses the shared
  physical-X/Z path;
- reordered noise inputs translate physical coordinates before scaling or
  reordering;
- positional random factories canonicalize X/Z pairs through the new geometry;
- carver and structure seeds use paired canonical coordinates;
- generation windows and spillover writes resolve offset aliases to one
  canonical owner;
- forced progression structures stay inside the square canonical bounds;
- the water-only seed preflight samples the full canonical square.

Do not add compact-torus or true periodic-lattice implementations for this
mode.

### 6. Atlas And Projector Behavior

The Atlas must distinguish this geometry by tiling mode, revision, and basis.
Existing square or hex discovery data must not be interpreted as
offset-square data.

Small literal-map tiles:

- keep the existing placed torus mesh and render path;
- use the A/B Atlas parameterization so opposite offset seams join correctly
  on the torus;
- do not add an offset-specific projector model;
- verify discovery, changed-column refresh, player markers, Atlas markers,
  completion, and travel through all six seam relations.

Held literal projection:

- retain a player-centered world-X/Z window;
- project each sampled world position through the offset A/B mapping;
- verify east/west travel scrolls into the half-tile-shifted north/south
  neighborhood;
- retain the existing pose, circular fade, grid, and player-facing rotation.

Large survey tiles:

- keep held and placed survey windows as local world-X/Z views;
- canonicalize every requested chunk through `OffsetSquareTileGeometry`;
- use nearest aliases for Atlas and player markers;
- verify the `128x128` held and `512x512` placed windows cover both halves of
  an east/west T-junction;
- keep the existing shallow placed survey surface.

Update `AtlasTorusProjection` documentation from “square and hex” to
geometry-neutral lattice terminology. Its determinant remains `W * W`, so
canonical area and discovery rewards need no offset-specific formula.

### 7. Debugging And Commands

Reuse geometry descriptions in:

- the F3 debug HUD;
- the F3+Y boundary renderer;
- `/globeworld pos`;
- `/globeworld border_distance`;
- `/globeworld teleport_border`.

The boundary renderer should draw nearby offset squares using lattice
translations and show the canonical square distinctly. Seam labels must report
`±A`, `±B`, and `±(A-B)` consistently at split east/west edges.

Add deterministic teleport/test locations for:

- north and south edge centers;
- upper and lower halves of east and west;
- both east/west midpoint T-junctions;
- all four canonical corners.

### 8. Documentation And Lifecycle

When implementation begins, keep this file as the active checklist. Once the
mode is implemented:

- add the durable geometry contract to
  `docs/mod-mechanics/topology.md`;
- document lattice edge blending in
  `docs/mod-mechanics/worldgen.md`;
- document literal and survey Atlas behavior in
  `docs/mod-mechanics/maps.md`;
- update `docs/mod-mechanics/README.md`;
- reduce this plan to unresolved validation/follow-up work or move it to the
  implemented section of the plans index.

## Automated Tests

### Geometry

Add offset-square coverage to `TileGeometryTest`:

- configured odd widths sanitize upward to even widths;
- canonical bounds contain exactly `W * W` chunks;
- every canonical chunk maps to itself;
- a wide raw-coordinate sample maps inside the canonical square;
- translations by `A`, `B`, and `A-B` preserve canonical chunks and blocks;
- `latticeCoordinate` and `latticeTranslation` round-trip;
- block-local coordinates survive canonicalization;
- nearest aliases are correct around all six neighbors;
- wrapped distance is symmetric and selects the shortest lattice copy;
- canonical query boxes cover north/south seams, both east/west half-seams,
  midpoint T-junctions, and corners;
- boundary segments expose the expected six labels and correct outside aliases;
- Atlas canonical chunk count is `W * W`.

Run these tests at minimum widths and at representative widths such as `2`,
`8`, `16`, and `32` chunks.

### Terrain And Randomness

Extend `PeriodicNoiseUtilTest`:

- lattice blend samples are invariant under `A`, `B`, and `A-B`;
- paired seam values and finite-difference slopes agree;
- weights remain finite for the minimum tile;
- physical X/Z translation occurs before scale and input reordering;
- positional random factories agree at lattice-equivalent block and chunk
  coordinates;
- ordinary square and hex regression fixtures remain unchanged.

### Atlas

Add projection/window tests:

- `p`, `p+A`, `p+B`, and `p+(A-B)` select the same literal Atlas pixel;
- pixel-to-world samples canonicalize deterministically;
- held local windows cross east/west with the expected Z offset;
- survey cells and markers choose the viewer-nearest alias;
- windows at a T-junction include both neighboring offset frames;
- projection identities differ from ordinary square and hex identities.

## Manual Acceptance Matrix

For each of `+A`, `-A`, `+B`, `-B`, `+(A-B)`, and `-(A-B)`:

- cross on foot, by flight, in a vehicle, and with a projectile;
- place and break blocks;
- test neighbor updates, redstone, fluids, and lighting;
- observe entity tracking, collisions, sensing, and path targets;
- trigger explosions, sounds, particles, and game events;
- inspect terrain, biomes, caves, aquifers, surfaces, carvers, features, and
  structures;
- save, reload, and approach the seam from a different alias.

At both east/west midpoint T-junctions and all four corners:

- verify canonical ownership is deterministic;
- verify queries do not miss or duplicate entities;
- verify packet fanout does not show duplicate block/entity state;
- verify blend output remains continuous;
- verify debug seam labels select the expected neighbor.

Atlas acceptance:

- walk east/west across both half-seams with a held Atlas and confirm the map
  scrolls into the offset north/south neighborhood;
- walk north/south and confirm ordinary local scrolling;
- inspect a small placed torus across its texture seams;
- inspect held and placed large-tile survey windows near every seam relation;
- verify markers, discovery, refresh, rewards, and linked travel.

Local-time acceptance:

- compare local solar time at positions separated by `A`, `B`, and `A-B`;
- confirm north/south aliases retain the same longitude;
- observe sky/lightmap continuity while crossing all six seam relations.

## Definition Of Done

The feature is complete when:

- `OFFSET_SQUARE` worlds can be created through the custom settings UI;
- tile width is always an even whole-chunk value;
- the canonical `W x W` square owns all mutable state exactly once;
- columns alternate between aligned and half-offset positions;
- runtime gameplay works through all six seam relations and both T-junctions;
- continuous worldgen fields are invariant under the offset lattice;
- discrete worldgen passes the same documented acceptance level as hex mode;
- scrolling local time is alias-invariant and north/south-consistent;
- handheld and large survey projections show local offset-square adjacency;
- small placed projectors retain the existing torus render path;
- ordinary square and hex automated regressions remain unchanged;
- durable behavior is documented under mod mechanics.
