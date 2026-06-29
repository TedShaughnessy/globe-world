# Seamless Hexagonal Edge Blending

Status: continuous base-terrain blending and pairwise positional randomness are
implemented. The remaining work is integration/manual acceptance for discrete
carvers, features, structures, external synthetic regions, and the optional
continuous-band debug renderer.

This plan is the terrain-generation follow-up to
[Hexagonal tiles](hexagonal-tiles.md). It preserves the discrete whole-chunk
ownership mask while blending terrain against the continuous hexagonal
Voronoi cell beneath that mask.

## Goal

Generate a hexagonal Overworld whose terrain joins continuously across all six
seams while changing as little vanilla generation as possible.

The intended result is:

- vanilla sampling remains unchanged in the safe interior;
- only a bounded band around the ideal six-sided boundary is blended;
- the blend band follows straight continuous hex sides, not the chunk
  staircase;
- paired edges and all six vertices agree under the `A`/`B` lattice
  translations;
- the square topology and its existing terrain modes remain unchanged.

Vanilla noise is not periodic, so exact vanilla terrain and seamless wrapping
cannot both hold everywhere. This design localizes the compromise to the
smallest practical seam band.

## Scope

The first implementation covers the continuous inputs that determine base
terrain:

- biome climate noise;
- terrain density and noodle/cave noise;
- aquifer noise fields;
- surface depth, secondary surface, badlands, frozen-ocean, and clay-band
  noise;
- noise-backed state providers and placement counts already routed through
  `PeriodicNoiseUtil`;
- legacy `BlendedNoise` paths still active in supported generators.

Discrete generation is not numerically blended. Positional randomness,
carvers, features, structures, and their writes must instead use canonical
hex coordinates and the existing wrapped generation/spillover machinery.
Those audits are required before calling all of hex worldgen seamless, but
they should not be mixed into the scalar blend algorithm.

Initial support remains Overworld-only. Nether hex stays disabled until the
Overworld implementation and acceptance matrix are stable.

## Non-Goals

- Do not change the whole-chunk canonical ownership contract.
- Do not make the saved mask block-precision or migrate existing hex
  ownership.
- Do not replace vanilla noise with oblique periodic, Fourier, or compact
  torus noise.
- Do not blend final block states, selected biomes, feature decisions, or
  structure pieces.
- Do not alter square `EDGE_BLEND`, `COMPACT_TORUS`, or `PERIODIC_LATTICE`
  output.
- Do not promise exact vanilla terrain inside a seam band.

## Geometry Decision

The terrain blend uses the ideal continuous Voronoi cell of the same block
lattice that defines `HexTileGeometry`:

- `A = (horizontal spacing, half height)`;
- `B = (0, height)`;
- the third edge relation is `A-B`;
- the six neighboring cells are `±A`, `±B`, and `±(A-B)`.

The current chunk mask is already obtained by assigning chunk centers to their
nearest lattice center. Its staircase is therefore a discrete sampling of this
continuous hex. Chunk ownership and the debug boundary remain stepped; the
terrain transition does not.

For a point `q` relative to one lattice center and a neighboring translation
`t`, the relevant Voronoi half-plane is:

`q dot t <= lengthSquared(t) / 2`

The perpendicular signed distance inside that side is:

`(lengthSquared(t) / 2 - q dot t) / length(t)`

The signed distance for the whole ideal hex is the minimum over the six
neighbors. It is positive inside, zero on a straight ideal side, and negative
outside. Distances and blend widths are measured in blocks perpendicular to a
side, not independently along X and Z.

This continuous geometry should be exposed by a small immutable sampler or
descriptor derived from `TileGeometry.LatticeBasis`. Do not derive terrain
weights from `boundarySegments()`, because those segments intentionally
describe the exact chunk staircase.

## Blend Contract

Let `N(p)` be one unmodified vanilla scalar sample at horizontal world
position `p`. For nearby lattice translations `t`, define:

`F(p) = sum(weight(p, t) * N(p - t)) / sum(weight(p, t))`

Build each raw cell weight from the six individual half-plane distances, not
only from their minimum. Pass every distance through the same symmetric
smoothstep gate, multiply the six gates, and normalize the resulting weights
across translated cells. Using the minimum distance directly would leave a
derivative crease where the nearest side changes around a vertex.

Each half-plane gate is:

- zero when the representative is at least one blend width outside that side;
- one when it is at least one blend width inside that side;
- a symmetric smoothstep transition between those limits;
- value- and first-derivative-continuous at both limits.

For signed side distance `d` and blend width `w`, the initial gate should be:

`gate(d) = smoothstep(clamp((d + w) / (2 * w), 0, 1))`

The product therefore has one origin weight and zero neighbor weights
throughout the safe interior. Normalization handles side bands, vertices, and
overlapping bands without assigning priority to an owner or tie-break winner.

Shifting `p` by any lattice translation only permutes the representatives in
the sum. The blended result must therefore satisfy:

- `F(p) == F(p + A)`;
- `F(p) == F(p + B)`;
- consequently, `F(p) == F(p + A - B)`.

Along an ordinary side, two translated vanilla samples contribute. At a
Voronoi vertex, the three cells meeting there contribute without an
origin-cell preference.
Candidate selection must be general enough for overlapping bands on minimum
tile sizes rather than assuming that every sample has at most three
contributors.

Use one shared kernel for every continuous worldgen field so biome, density,
cave, and surface transitions occupy the same geometric band.

## Blend Width

Start with the square edge-blend intent: derive the width from tile scale and
cap it to a practical block range. For hex, use a characteristic size from the
continuous cell, such as its inradius, rather than `tileSizeBlocks()` as an
axis period.

The final policy must:

- use one deterministic width for a dimension and geometry revision;
- preserve a non-blended interior on ordinary tile sizes;
- remain correct when bands overlap on minimum tiles;
- be reported in worldgen diagnostics.

The band does not need to cover the displacement between the ideal boundary
and the chunk staircase. Global lattice-periodicity makes the terrain agree at
any ownership cut, while the actual blend remains centered on the straight
ideal side. Do not widen the band merely to chase chunk edges.

The initial tuning target is comparable to the existing 64–256 block square
band. If minimum hexes cannot retain a useful safe interior at that width,
correct seamless output takes priority and the limitation should be documented
rather than introducing a discontinuity.

Implemented result: width `8` has an approximately `57.7`-block ideal-cell
inradius and the blend width clamps to `64`, so it is a fully blended
micro-hex with overlapping bands and no one-contributor safe interior. Width
`12` is the smallest supported hex with a safe interior. The width-`8` result
remains finite and invariant under both lattice translations.

## Sampling API

`PeriodicNoiseUtil.samplePlane(first, second, ...)` is too ambiguous for this
work. Some vanilla calls reorder noise inputs even though both source
coordinates represent physical X/Z. Hex translations must be applied in
physical world space before scale, offset, or input reordering.

Add an explicit horizontal API with this conceptual shape:

```java
sampleHorizontal(blockX, blockZ, (translatedX, translatedZ) -> vanillaSample)
```

The callback remains responsible for mapping translated physical coordinates
onto the vanilla noise method's input order. This is necessary for paths such
as `ShiftB`, which samples its noise with reordered horizontal components.

The dispatch contract is:

- disabled topology: one untouched vanilla sample;
- square topology: retain current square-mode behavior exactly;
- hex `EDGE_BLEND`: use continuous lattice weights and translated vanilla
  samples;
- no horizontal helper may treat Y as a periodic hex coordinate.

Translate block coordinates before applying a noise scale. Existing domain
offsets, including density-function shifts, remain part of the vanilla sample.
Because their source fields are also periodic, equivalent lattice positions
receive equivalent offsets.

The hot path should avoid collections and per-sample allocation. Compute
candidate translations and nonzero weights first, then invoke the expensive
vanilla sampler only for contributors. The expected cost is one sample in the
interior, two near a side, and three near a vertex.

## Continuous Worldgen Hook Audit

Migrate and classify every current `PeriodicNoiseUtil` caller:

1. Density primitives:
   `DensityFunctionsNoiseMixin`, `DensityFunctionsShiftMixin`,
   `DensityFunctionsShiftAMixin`, `DensityFunctionsShiftBMixin`,
   `DensityFunctionsShiftedNoiseMixin`, and
   `DensityFunctionsWeirdScaledSamplerMixin`.
2. Legacy terrain:
   `BlendedNoiseMixin`.
3. Surface shaping:
   `SurfaceSystemMixin`.
4. Noise-backed decoration:
   `DualNoiseProviderMixin`, `NoiseBasedStateProviderMixin`,
   `NoiseBasedCountPlacementMixin`, and
   `NoiseThresholdCountPlacementMixin`.

For each hook, record:

- which arguments are physical X and Z;
- which values are scaled/reordered noise inputs;
- whether Y remains fixed or independently sampled;
- whether the hook blends a continuous scalar or makes a discrete decision.

Blend continuous scalar inputs before vanilla converts them into a biome,
block state, count, threshold result, or other category. Do not interpolate
those categorical results after selection.

## Positional Randomness And Discrete Generation

`PeriodicPositionalRandomFactory` currently wraps X and Z independently. That
is not a valid hex identity operation. Replace its horizontal-period model
with a coordinate-unit-aware geometry operation:

- block-unit factories canonicalize a complete block X/Z pair;
- chunk-unit factories canonicalize a complete chunk X/Z pair;
- Y passes through unchanged;
- lattice-equivalent positions produce the same random stream.

Audit direct independent wrapping in:

- `SurfaceSystemMixin`;
- `NoiseBasedChunkGeneratorMixin`;
- `StructureGenerationContextMixin`;
- `StructurePlacementMixin`;
- `RandomSpreadStructurePlacementMixin`;
- forced-progression helpers and any remaining worldgen `CoordUtil` matches.

Then validate each discrete stage:

- aquifer and ore random factories;
- carver seeds, origins, and writes;
- feature placement seeds, origins, neighbor reads, and spillover writes;
- structure starts, references, shifted placement, and forced progression
  structures.

Canonicalizing randomness makes decisions repeat; `GenerationWindow` and
spillover remain responsible for making cross-seam reads and writes reach the
single canonical owner. Neither mechanism should create durable alias-owned
generation state.

## Implementation Milestones

### 1. Continuous Hex Geometry

Status: implemented in `LatticeBlendGeometry` and exposed by `TileGeometry`.

- Add a block-space lattice blend descriptor derived from `TileGeometry`.
- Expose the six neighbor translations, ideal signed distance, inradius, and
  deterministic blend width without exposing `HexTileGeometry` casts to
  callers.
- Keep exact staircase boundary APIs unchanged for ownership and diagnostics.
- Add pure tests for half-planes, side distances, vertices, translation
  invariance, and minimum/large tile sizes.

### 2. Scalar Blend Kernel

Status: implemented in `PeriodicNoiseUtil`, including physical-X/Z sampling,
overlapping-band candidates, and focused invariance/contributor tests.

- Add a no-allocation hex blend path to `PeriodicNoiseUtil`.
- Add the explicit physical-X/Z horizontal sampling API.
- Preserve one-sample, bit-identical vanilla behavior in the safe interior.
- Support all contributors needed when bands overlap.
- Add optional `WORLDGEN` diagnostics for contributor count, blend width, and
  nearest ideal side without logging every sample by default.

### 3. Terrain And Biome Hooks

Status: implemented for the listed density, shift, cave/aquifer-noise, and
legacy `BlendedNoise` scalar hooks. Visual seed fixtures remain to be run.

- Migrate density, shift, cave, aquifer-noise, and `BlendedNoise` hooks.
- Verify climate values are blended before biome selection.
- Compare generated heightmaps, biome grids, and cave sections at all three
  seam pairs and all six vertices.
- Confirm square generation is unchanged for identical seeds/settings.

### 4. Surface And Noise-Backed Decoration

Status: implemented for the listed continuous surface/provider noise and
known-unit surface positional randomness. Manual material checks remain.

- Migrate surface-system noise and its positional randomness.
- Migrate noise-backed state/count providers to physical horizontal sampling.
- Check badlands, frozen oceans, terracotta bands, bedrock-adjacent terrain,
  and surface material transitions across every seam.

### 5. Discrete Worldgen Completion

Status: partially implemented. Block/chunk positional random factories and
carver/structure seed calls now canonicalize complete X/Z pairs. The broader
feature, structure, spillover, and side-effect acceptance matrix remains.

- Replace remaining split-axis hex seed/random canonicalization.
- Complete carver, feature, structure-reference, and spillover audits.
- Verify trees, ores, lakes, geodes, structures, block entities,
  post-processing marks, and scheduled side effects at seams.
- Keep unsupported external synthetic worldgen regions on the documented
  conservative path.

### 6. Product And Documentation

- Add an optional debug view of the ideal continuous hex and blend-band
  boundaries alongside the exact chunk staircase.
- Report the resolved hex blend width and geometry revision in debug output.
- Once implemented, move the durable algorithm and code anchors into
  [Worldgen](../mod-mechanics/worldgen.md).
- Update this plan and [Hexagonal tiles](hexagonal-tiles.md) to retain only
  unresolved investigations.

## Automated Tests

Add focused tests that do not require visual judgment:

- origin-cell weights are exactly one/zero outside the band;
- weights are finite, non-negative, and normalize to one;
- translating a sample by `A`, `B`, or `A-B` preserves the blended value;
- paired side samples agree to a tight floating-point tolerance;
- each three-cell vertex gives permutation-symmetric results;
- finite-difference slopes agree across paired seams;
- points on the chunk staircase have no special weight discontinuity;
- minimum tiles remain finite and translation-invariant with overlapping
  bands;
- an instrumented sampler is called once in the safe interior, twice on an
  ordinary edge, and three times at a non-overlapping vertex;
- nonzero scales and X/Z input reordering translate world coordinates before
  transforming noise inputs;
- hex positional random factories agree at all lattice-equivalent block and
  chunk positions;
- square sampler and positional-random behavior remain unchanged.

Add deterministic integration fixtures for one ordinary seed and one
deliberately difficult ocean/mountain seed. Compare:

- height and density samples across each seam relation;
- biome climate tuples and selected biomes;
- cave/aquifer presence near paired edges;
- surface columns;
- repeated results under different chunk generation orders and after reload.

## Manual Acceptance Matrix

For each of `+A`, `-A`, `+B`, `-B`, `+(A-B)`, and `-(A-B)`:

- inspect terrain at lowlands, mountains, coastlines, rivers, and caves;
- cross the seam on foot and in spectator mode below ground;
- confirm biome, grass/water color, surface material, and sea level join;
- confirm the blend does not trace chunk-sized staircase steps;
- inspect trees, ores, carvers, fluids, and structures that cross the seam;
- save, reload, and regenerate neighboring chunks in a different order.

At all six ideal vertices:

- confirm the three translated views show the same terrain;
- look for spikes, pits, biome wedges, cave caps, water walls, and repeated
  decorations;
- compare the exact staircase overlay with the continuous blend overlay to
  ensure ownership remains truthful while terrain stays visually continuous.

Profile an ordinary tile and the minimum supported tile. Record generation
time, scalar sample multiplier, allocation rate, and worst contributor count.

## Completion Criteria

The continuous terrain milestone is complete when:

- density, biome climate, caves, aquifer fields, and surfaces are
  lattice-equivalent at every tested side and vertex;
- the untouched interior matches vanilla sampling for the same seed and
  coordinates;
- no chunk-staircase-shaped terrain transition is visible;
- generation is deterministic across order and reload;
- square terrain output has no regression;
- generation cost remains acceptable and the hot blend path does not allocate.

Full seamless hex worldgen additionally requires the discrete carver, feature,
randomness, spillover, and structure acceptance items above. Until those pass,
documentation should say “seamless base terrain” rather than “seamless
worldgen.”
