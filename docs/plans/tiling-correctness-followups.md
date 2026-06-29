# Coupled-Tiling Correctness Follow-Ups

Status: implemented. Hex v2 ownership, exact coupled-lattice query
decomposition, dual-basis visual alias bounds, and automated regressions are in
place. The manual acceptance matrix below remains to be run.

## Goal

Make hex and offset-square topology satisfy the same geometry contract at every
supported tile size:

- every lattice orbit has exactly one canonical owner;
- translating by any lattice vector preserves canonical identity;
- broad queries return exactly the identities visible in the requested frame;
- visual alias enumeration cannot omit an alias inside its configured radius;
- tile-size policy reflects geometry and terrain behavior rather than working
  around implementation bugs.

This work should preserve ordinary square behavior and the direct
offset-square canonicalization formulas.

## Decisions

### Hex Size Policy

Keep the permanent saved-size rule:

```text
W >= 8 chunks
W is a multiple of 4 chunks
```

Do not introduce a whitelist of apparently safe sizes. The former hex
tie-break defect occurred at an irregular set of widths, including `12`, `20`,
`28`, and `40`, so raising the minimum or allowing only a simple parity class
would conceal rather than solve the ownership problem.

The minimum supported width remains `8` chunks (`128` blocks):

- its basis is `A = (6, 4)` and `B = (0, 8)` chunks;
- its lattice determinant and canonical ownership area are `48` chunks;
- its ideal-cell inradius is about `57.7` blocks;
- the blend width is clamped to the `64`-block minimum.

Width `8` is therefore geometrically valid, but it has no fully unblended
terrain interior. It should be documented as a fully blended micro-hex with a
higher relative worldgen cost. After the ownership fix, width `12` is the
smallest supported hex with an unblended interior. Width `16` is the smallest
such size that happened not to expose the former known tie defect, but that
is not a sufficient reason to make `16` the permanent minimum.

### Geometry Revision

Changing hex boundary tie-breaking changes canonical chunk ownership and must
not silently retain `hex-east-west-v1`.

The corrected geometry revision is:

```text
hex-east-west-v2
```

Implemented compatibility policy:

- Experimental v1 canonical worlds are invalidated rather than retaining a
  second ownership implementation.
- The settings UI and topology documentation warn users to back up v1 worlds
  and create a new v2 world.
- Atlas and survey projection identities reset through `hex-east-west-v2`.
- The old settings did not persist geometry revision, so v1 cannot be
  automatically detected or safely migrated from mode and tile size alone.

## Required Changes

### 1. Make Hex Ownership Tie-Breaking Translation-Invariant — Implemented

The former hex canonicalization found the nearest lattice center and resolved
equal distances through `LatticeOffset.compareTo(...)`. That comparator prefers
the absolute origin, then the smallest absolute `(k, l)` length. Neither rule is
preserved when every candidate is translated by the same lattice coordinate.

An allowed width of `12` demonstrates the failure:

```text
A = (9, 5)
chunk p = (4, 2), center = (4.5, 2.5)
p - A = (-5, -3), center = (-4.5, -2.5)
```

`p` is equidistant from lattice centers `0` and `A`. Its alias `p-A` is
equidistant from `-A` and `0`. Absolute-origin preference chooses center `0` in
both cases, so both chunks are treated as canonical even though they belong to
the same lattice orbit. The resulting origin-selected mask contains `92`
chunks while the lattice determinant is `90`.

Replace the ownership tie-break with an ordering invariant under:

```text
(candidateK, candidateL) -> (candidateK + translationK, candidateL + translationL)
```

A lexicographic ordering of candidate lattice coordinates has this property
because adding the same coordinate to both candidates preserves their order.
Do not use absolute-origin preference, distance from the absolute origin, or
another global-center bias.

Keep ownership and viewer-nearest presentation decisions conceptually
separate. If presentation needs an origin-preferring tie at an exactly
equidistant viewer position, it may use a separate nearest-alias policy; it
must not control canonical identity.

Update:

- `globe.world.topology.HexTileGeometry`;
- the geometry revision and any persisted projection identities;
- topology documentation describing the half-open/tie ownership rule.

### 2. Preserve Both Axes In Coupled-Lattice Broad Queries — Implemented

`HexTileGeometry.canonicalQueryBoxes(...)` and
`OffsetSquareTileGeometry.canonicalQueryBoxes(...)` formerly returned the full
canonical bounds when either raw box dimension spans a tile dimension:

```text
wide in X OR wide in Z -> full canonical owner in X and Z
```

That is not a valid implication for a coupled two-dimensional lattice. A box
that wraps fully around one quotient direction can remain narrow in the other.
Returning the full owner produces false-positive entity identities because
`TopologicalEntityQueries` does not universally re-test non-player candidates
against a visible alias box.

For example, in a minimum offset-square world, a query with:

```text
X = [-16, 16]
Z = [-1, 1]
```

spans the complete `32`-block X period but must not include an entity at
canonical `Z = 10`. The former shortcut returned the full `32 x 32` canonical
square and can include it.

Implement exact coupled-lattice decomposition:

- estimate the relevant lattice-coordinate range from the complete query box;
- translate the query into each potentially intersected canonical frame;
- intersect with the canonical owner bounds/mask as required;
- deduplicate identical slices;
- preserve the narrow quotient direction when the other direction wraps;
- only collapse to the complete canonical owner when coverage of the complete
  two-dimensional quotient is proven.

Avoid using raw `X-size >= width` or `Z-size >= height` as a sufficient
two-dimensional coverage test.

If a conservative broad phase is retained for performance, every consumer must
apply a geometry-correct visible-alias intersection filter before returning
identities. Exact decomposition is preferred because the query-box API already
claims to describe canonical slices.

Update:

- `globe.world.topology.HexTileGeometry`;
- `globe.world.topology.OffsetSquareTileGeometry`;
- any reusable coupled-lattice query helper extracted from them;
- `TopologicalEntityQueries` only if a final candidate filter remains
  necessary.

### 3. Bound Visual Alias Enumeration In Lattice-Coordinate Space — Implemented

`GlobeEntityAliasing.visualLatticeOffsets(...)` converts a render radius into
one shared `(k, l)` search radius by dividing by the shortest Euclidean lattice
translation. That distance does not bound lattice coefficients in an oblique
basis: large `k` and `l` can partially cancel.

For a width-`8` hex:

```text
A = (6, 4) chunks
B = (0, 8) chunks
(k, l) = (10, -5)
k*A + l*B = (60, 0) chunks = (960, 0) blocks
```

The former shortest-vector divisor was about `115.4` blocks, so a
`960`-block render radius searches only through coefficient radius `9` and
omits the in-range `(10, -5)` alias.

Derive coefficient bounds from the dual basis. In block units:

```text
det = abs(Ax*Bz - Az*Bx)
kAltitude = det / length(B)
lAltitude = det / length(A)
kRadius = ceil(searchDistance / kAltitude)
lRadius = ceil(searchDistance / lAltitude)
```

Either enumerate independent `kRadius` and `lRadius` values or use their
maximum when an existing API requires one shared radius. Include entity-box
padding in `searchDistance`, then retain the final point-to-box distance test.
The configured ring limit remains an intentional cap and may still reduce the
enumeration.

Put this calculation in a small geometry-neutral lattice helper rather than
reintroducing a hex cast. `LatticeBlendGeometry` already computes related
altitudes for its candidate search; share the mathematical concept without
coupling terrain blending to entity rendering.

Update:

- `globe.world.util.GlobeEntityAliasing`;
- `TileGeometry` or a small lattice-math helper if that produces a clearer
  capability boundary;
- diagnostics that report an unlimited search radius, if applicable.

## Automated Tests

### Hex Ownership Properties

Test every multiple-of-four width from `8` through at least `256`, plus selected
larger widths.

For each width:

- count the canonical mask and assert it equals the lattice determinant;
- assert every canonical chunk maps to itself;
- translate every canonical chunk by `±A`, `±B`, and `±(A-B)` and assert it
  maps back to the same owner;
- repeat with several larger `(k, l)` translations;
- assert block-local coordinates survive every translation;
- assert `latticeCoordinate` and `latticeTranslation` round-trip;
- include explicit width-`12` fixtures for `(4, 2)` and `(-5, -3)`;
- include randomized raw chunks around boundary sides and vertices.

These are property tests over the complete small masks, not only checks using
canonical chunk `(0, 0)`.

### Broad Query Exactness

For hex and offset-square:

- test boxes narrower than one period near every seam and vertex/T-junction;
- test X-wide/Z-narrow and X-narrow/Z-wide boxes;
- test boxes exactly one period wide and just below/above one period;
- compare the returned canonical slices with a brute-force point/chunk oracle;
- assert a canonical entity outside the narrow direction is not returned;
- retain deduplication checks where three frames meet.

Ordinary square query output must remain unchanged.

### Visual Alias Completeness

For representative coupled bases:

- brute-force lattice coordinates in a generous range;
- collect every alias box within the render radius;
- assert the production enumerator returns the same set after the intentional
  ring cap;
- include width-`8` hex `(10, -5)` at a `960`-block radius;
- test both finite and unlimited ring settings;
- test an offset-square cancellation direction;
- assert no duplicate `(k, l)` aliases are returned.

### Terrain Regression

Retain the existing blend tests and explicitly record:

- width `8` remains finite and translation-invariant with overlapping bands;
- width `8` is not expected to have a one-contributor safe interior;
- width `12` has a safe interior after the ownership fix;
- ownership tie changes do not change the continuous lattice blend basis.

## Manual Acceptance

After automated tests pass:

- create width-`8`, width-`12`, and width-`16` hex worlds;
- inspect all six seams and vertices with F3+Y;
- place blocks in both raw positions from the former width-`12` duplicate
  ownership fixture and confirm they resolve to one state;
- run entity queries whose box wraps one axis but remains narrow in the other;
- use unlimited visual alias rings at a large render radius and inspect aliases
  along near-cancelling lattice directions;
- save, reload, and verify that the selected geometry-revision policy behaves
  as documented.

## Documentation Updates

When implemented:

- update [Topology](../mod-mechanics/topology.md) with the new hex revision and
  translation-invariant boundary rule;
- update [Entities](../mod-mechanics/entities.md) with the exact coupled-query
  and visual-alias enumeration guarantees;
- update [Worldgen](../mod-mechanics/worldgen.md) to describe width `8` as
  fully blended;
- update [Hexagonal tiles](hexagonal-tiles.md) and
  [Seamless hexagonal edge blending](hexagonal-edge-blending.md);
- move this plan to the implemented section or reduce it to any remaining
  migration/manual-validation work.

## Definition Of Done

This work is complete when:

- every supported hex width has exactly `abs(det(A,B))` canonical chunks;
- canonical ownership is invariant under arbitrary lattice translations;
- no coupled query broadens an untouched quotient direction;
- visual aliases inside the requested radius are complete unless excluded by
  an explicit ring cap;
- width `8` remains supported and is accurately described as fully blended;
- the hex geometry revision and old-save policy are explicit;
- ordinary square and offset-square ownership regressions remain unchanged.
