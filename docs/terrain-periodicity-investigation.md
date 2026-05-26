# Terrain Periodicity Investigation

## Problem

The first seamless terrain pass made many terrain, biome, surface, feature-count,
and random sources periodic by routing X/Z sampling through `PeriodicNoiseUtil`.
Exploration shows the resulting worlds can look worse than default Minecraft
terrain.

The likely root cause is that the current sampler guarantees repetition by
embedding each 2D sample point on a sine/cosine torus and averaging four vanilla
noise samples:

- `src/main/java/globe/world/util/PeriodicNoiseUtil.java`
- `src/main/java/globe/world/mixin/DensityFunctionsNoiseMixin.java`
- `src/main/java/globe/world/mixin/BlendedNoiseMixin.java`
- `src/main/java/globe/world/mixin/SurfaceSystemMixin.java`

This is periodic, but it is not vanilla-equivalent noise with periodic
boundaries. It changes the sample-space geometry and the output distribution
across the whole tile.

## Vanilla Source Anchors

Common source jar:

`.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-52430b475d/26.1.2/minecraft-common-52430b475d-26.1.2-sources.jar`

Relevant vanilla classes:

- `net/minecraft/world/level/levelgen/synth/ImprovedNoise.java`
  - Gradient noise hashes integer lattice coordinates through a 256-entry
    permutation table.
- `net/minecraft/world/level/levelgen/synth/PerlinNoise.java`
  - Octave stack over `ImprovedNoise`, using per-octave input and value factors.
- `net/minecraft/world/level/levelgen/synth/NormalNoise.java`
  - Sum of two `PerlinNoise` stacks, with the second stack sampled at
    `1.0181268882175227` times the input.
- `net/minecraft/world/level/levelgen/synth/SimplexNoise.java`
  - Used by `PerlinSimplexNoise` for some biome/temperature/placement noise.
- `net/minecraft/world/level/levelgen/DensityFunctions.java`
  - Terminal noise density functions: `Noise`, `Shift`, `ShiftA`, `ShiftB`,
    `ShiftedNoise`, and `WeirdScaledSampler`.

## Why The Current Method Can Look Worse

### Global domain warping

Every sample in the tile is bent onto a circle in X and a circle in Z. Vanilla's
large-scale terrain assumes a flat noise plane; the current pass samples curved
paths through the original noise field. That makes the whole tile feel less like
a normal local patch of Minecraft, not just the seam area.

### Distribution changes

The four-sample average reduces variance and changes correlations. Terrain can
become flatter, more rounded, more repetitive, or less decisive because many
density functions see softened noise.

### Frequency compression on small tiles

Very small tiles cannot contain vanilla-scale continental structure honestly.
For example, a 1,600-block tile already asks all continental, erosion, ridge,
cave, aquifer, surface, and feature patterns to close on themselves inside that
space. A 16- or 32-block tile is necessarily stylized, no matter which sampler is
used.

### Broad hook surface

The current pass intercepts a lot of sources. That is good for seam coverage,
but it also means any statistical distortion is amplified through terrain shape,
biome climate, caves, aquifers, surface materials, and decorations.

## Better Mechanism: Periodic Lattice Noise

The higher-quality default should be a true periodic version of vanilla's own
noise stack.

For `ImprovedNoise`, keep vanilla interpolation, gradients, offsets, y handling,
and octave composition. The horizontal sample phase must complete an integer
number of lattice cells over the tile, then the integer lattice indices used for
X/Z hashing can wrap:

```text
targetCells = blocksPerTile * octaveInputFactor * externalScale
periodCells = max(1, round(targetCells))
adjustedExternalScale = periodCells / (blocksPerTile * octaveInputFactor)

sampleX = blockX * adjustedExternalScale * octaveInputFactor
sampleZ = blockZ * adjustedExternalScale * octaveInputFactor

latticeX = floor(sampleX + xo)
latticeZ = floor(sampleZ + zo)

hashX0 = floorMod(latticeX, periodCells)
hashX1 = floorMod(latticeX + 1, periodCells)
hashZ0 = floorMod(latticeZ, periodCells)
hashZ1 = floorMod(latticeZ + 1, periodCells)
```

The fractional position inside the cell should remain unchanged for the adjusted
sample coordinate. Y should stay unwrapped.

This preserves vanilla's local noise behavior much better than torus embedding:

- Same gradient-noise shape.
- Same octave amplitudes and value factors.
- Same vertical behavior.
- Same rough distribution.
- Exact equality at `x + W_BLOCKS` and `z + W_BLOCKS` when each octave's lattice
  period is integer-safe.

## Hard Part: Octave Periods

Vanilla octave input periods are not always integer tile periods after scaling.
For exact wrapping, each octave needs an integer lattice period in X/Z. This is
especially relevant for `NormalNoise`, because its second `PerlinNoise` stack is
sampled at `1.0181268882175227` times the input.

Practical options:

1. Require or prefer tile sizes whose block period produces usable integer
   periods for important octave scales.
2. Quantize each octave's horizontal input factor slightly so the octave completes
   an integer number of cycles per tile.
3. Drop or replace octaves whose wavelength is larger than the tile can support.
4. Use the current torus embedding as a fallback when an exact lattice period is
   too small to be useful.

Option 2 is probably the best default for medium/large tiles. It trades tiny
frequency shifts for much more vanilla-like local terrain.

## Tile Size Implications

Tile size is configured in chunks, so `W_BLOCKS = tileChunks * 16`.

For the first `NormalNoise` Perlin stack, the lowest active octave has this many
lattice cells per tile:

```text
cells = tileChunks * 16 * xzScale * 2^lowestActiveOctave
```

Important vanilla Overworld thresholds:

| System | Lowest active octave / scale | Cells per chunk | Exact chunk multiple | Chunks for at least 1 cell |
| --- | ---: | ---: | ---: | ---: |
| Ridges / weirdness | `-7`, `0.25` | `1/32` | `32` | `32` |
| Vegetation | `-8`, `0.25` | `1/64` | `64` | `64` |
| Continentalness / erosion | `-9`, `0.25` | `1/128` | `128` | `128` |
| Temperature | `-10`, `0.25` | `1/256` | `256` | `256` |
| Large-biome continentalness / erosion | `-11`, `0.25` | `1/512` | `512` | `512` |
| Large-biome temperature | `-12`, `0.25` | `1/1024` | `1024` | `1024` |

So for the main landform/climate fields:

- `128` chunks is the first size where normal Overworld continentalness and
  erosion can close cleanly.
- `256` chunks is the first size where normal Overworld temperature also closes
  cleanly.
- `512` chunks is the first size where large-biome continentalness and erosion
  close cleanly.
- `1024` chunks is the first size where large-biome temperature closes cleanly.

The default `100` chunk tile is awkward for vanilla-scale terrain:

- Continentalness / erosion lowest octave: `100 / 128 = 0.78125` cells.
- Temperature lowest octave: `100 / 256 = 0.390625` cells.

That means a strict periodic lattice sampler must either quantize those octaves
to one cell, drop them, or use a different compact-tile terrain profile.

### Strict Exactness Is Not Practical

If every rational vanilla scale currently hooked by the terrain pass is required
to be exact, the least common chunk multiple is enormous: `537600` chunks. This
comes from scales such as `0.67`, `5/7`, `2/3`, `1.17`, and `1.28` in aquifers,
caves, and special surface systems.

If `NormalNoise`'s second Perlin stack and `BlendedNoise`'s legacy constants are
also required to keep their literal vanilla frequencies, exact chunk whitelisting
stops being useful. Those paths include constants such as
`1.0181268882175227` and `684.412`.

Therefore, a practical size limit should target the major low-frequency landform
octaves, while the implementation quantizes lesser/special octaves by a tiny
amount.

### Practical Size Tiers

Recommended lattice-mode tiers:

| Tier | Chunk sizes | Notes |
| --- | --- | --- |
| Compact / stylized | `< 128` | Use torus embedding or a purpose-built compact terrain profile. Vanilla-scale continents do not fit. |
| Minimum normal Overworld | `128` | Continents and erosion fit; temperature's lowest octave still needs adjustment. |
| Good normal Overworld | `256`, `512`, `768`, ... multiples of `256` | Main normal Overworld climate/landform low octaves fit exactly. |
| Best large-biome compatible | `1024`, `2048`, ... multiples of `1024` | Large-biome low octaves also fit exactly. |

If the UI needs a short curated list, a good first pass would be:

```text
16, 32, 64: compact/stylized
128: minimum normal globe
256: recommended normal globe
512: large normal globe
1024: large-biome-safe globe
```

## Large-Tile Alternative: Seam-Band Stitching

For large tiles where preserving default terrain is more important than every
interior sample being topologically generated, a seam-band mode may look best:

- Use vanilla noise in the interior.
- Blend toward periodic lattice noise only inside a configurable edge band.
- Use 100% periodic noise exactly at tile boundaries and corners.

This would keep most of the tile close to default Minecraft while guaranteeing
the visible wrap boundary closes. The tradeoff is a transition band, and features
that cross the band still need careful read/write and placement behavior.

This mode is less conceptually pure than periodic lattice noise everywhere, but
it may be the most pleasant option for large globe worlds.

### Edge-Blended Noise Maps

Another practical option is to keep vanilla noise scale exactly in the interior
and blend only the edge band so the sampled field tiles.

For a scalar noise sample `n(x, z)` inside a tile of width `W`, the sampler can
crossfade toward opposite-tile samples near the seam:

```text
base = n(x, z)

if x is near the west edge: eastCopy = n(x + W, z)
if x is near the east edge: westCopy = n(x - W, z)
if z is near the north edge: southCopy = n(x, z + W)
if z is near the south edge: northCopy = n(x, z - W)

corner bands blend four samples:
n(x, z), n(x +/- W, z), n(x, z +/- W), n(x +/- W, z +/- W)
```

Use a smoothstep weight with zero derivative at the band boundaries. At the
outermost edge, the opposite-tile sample has full weight, so values match exactly
across the wrap. Away from the edge band, vanilla sampling is untouched.

Tradeoffs:

- Preserves vanilla feature scale in the interior.
- Avoids stretching or squashing low octaves to fit the tile.
- Creates a visible or statistical transition band if the band is too narrow.
- Reduces variance in the blended band because independent samples are averaged.
- Needs corner-aware blending, otherwise X and Z seams fight each other.
- Best for medium and large arbitrary tile sizes, not tiny tiles where the seam
  band would dominate the whole world.

This is essentially a generated-noise version of making a texture tileable by
blending its borders, but applied at each noise source rather than to a finished
heightmap. Applying it per noise source keeps biomes, caves, surfaces, and
features more internally consistent than trying to blend final terrain heights.

## Terrain Mode Selection

The tile size input can stay as free chunk input. Internally, terrain generation
can select a periodic strategy based on tile size and noise fit:

| Mode | Suggested size/use | Behavior |
| --- | --- | --- |
| `COMPACT_TORUS` | tiny tiles, probably `< 64` chunks | Current torus-embedded sampler or a purpose-built compact profile. Seamless and stylized. |
| `EDGE_BLEND` | arbitrary medium/large sizes, especially non-clean sizes like `100` chunks | Vanilla noise in the interior, smooth border stitching near tile edges. No global scale change. |
| `PERIODIC_LATTICE` | clean sizes, especially multiples of `256` chunks for normal Overworld | True periodic version of vanilla gradient noise with octave periods chosen cleanly. |

Initial automatic policy:

```text
if tileChunks < 64:
    COMPACT_TORUS
else if tileChunks % 256 == 0:
    PERIODIC_LATTICE
else:
    EDGE_BLEND
```

This keeps arbitrary tile sizes usable, avoids globally deforming awkward sizes,
and still rewards clean sizes with the most topologically pure terrain.

## Small-Tile Mode

For very small tiles, the current torus-embedded sampler may remain useful:

- It is simple.
- It is guaranteed periodic.
- It handles awkward scales without per-octave period management.
- The terrain is already necessarily stylized because the tile is far smaller
  than vanilla terrain's natural wavelengths.

Small-tile mode should probably be presented as "compact / stylized seamless
terrain", not as default Minecraft terrain.

## Recommended Next Steps

1. Add a terrain periodicity mode:
   - `COMPACT_TORUS` for the current sampler and tiny tiles.
   - `EDGE_BLEND` for arbitrary medium/large tile sizes.
   - `PERIODIC_LATTICE` as the intended default.
   - Select the mode automatically from tile size, with optional debug override
     later if useful.
2. Implement a `PeriodicImprovedNoiseSampler` that mirrors vanilla
   `ImprovedNoise.sampleAndLerp` with wrapped X/Z lattice hash indices.
3. Implement periodic `PerlinNoise` and `NormalNoise` helpers around it.
4. Implement an edge-blended sampler that samples vanilla coordinates in the
   interior and blends opposite-tile samples in a configurable seam band.
5. Move density-function mixins from `PeriodicNoiseUtil.samplePlane(...)` to the
   selected sampler for the active terrain mode.
6. Handle `SimplexNoise` / `PerlinSimplexNoise` separately for biome info,
   temperature, and flower/feature placement noise.
7. Add debug comparison tooling:
   - Sample height/noise grids for vanilla, compact torus, edge blend, and
     periodic lattice.
   - Report min/max/mean/stddev and edge mismatch.
   - Optionally dump small PGM/CSV maps for quick visual comparison.
8. Test at representative tile sizes:
   - 1 chunk and 2 chunks: compact/stylized behavior.
   - 6 chunks: stress edge/corner behavior.
   - 100 chunks: default configured globe world using edge-blended arbitrary-size behavior.
   - 256 chunks: clean periodic-lattice normal Overworld behavior.
