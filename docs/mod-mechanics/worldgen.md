# Worldgen

## What

World generation must produce canonical chunks whose contents line up across
tile edges. Reads and writes during generation also need to respect the canonical
tile so features and structures can cross a seam without creating independent
alias state.

## Why

Runtime wrapping can make an already-generated finite tile behave continuous,
but it cannot make terrain, biomes, caves, surfaces, or structures visually
periodic after the fact. The generator's sample space needs a periodic strategy,
and worldgen side effects must write canonical state exactly once.

## Terrain Modes

`TerrainMode` selects a generation strategy from tile size:

- `COMPACT_TORUS`: tiny/stylized tiles.
- `EDGE_BLEND`: arbitrary medium/large sizes where preserving vanilla scale in
  the interior matters.
- `PERIODIC_LATTICE`: clean sizes where true periodic lattice noise can close
  the tile.

The current policy uses compact torus for small tiles, periodic lattice for
clean large multiples, and edge blend for awkward medium/large sizes.

## Implementation

Terrain and biome hooks route many X/Z-dependent samples through periodic noise
utilities or terrain-mode-aware sampling. Positional random factories are wrapped
where the caller's coordinate unit is known.

Alias `LevelChunk.postProcessGeneration` is cancelled and queued
post-processing offsets are cleared so alias neighbor-shape fixes do not write
through wrapped `Level.setBlock` into canonical storage.

Structure edge handling stores virtual source keys during reference generation,
resolves them during biome decoration, and places vanilla starts with a
whole-tile chunk-box shift. Alias starts are treated as transient worldgen data.

## Key Files

- `src/main/java/globe/world/util/TerrainMode.java`
- `src/main/java/globe/world/util/PeriodicNoiseUtil.java`
- `src/main/java/globe/world/util/PeriodicPositionalRandomFactory.java`
- `src/main/java/globe/world/util/WorldGenSpillover.java`
- `src/main/java/globe/world/util/StructurePlacementShifts.java`
- `src/main/java/globe/world/mixin/DensityFunctionsNoiseMixin.java`
- `src/main/java/globe/world/mixin/DensityFunctionsShiftMixin.java`
- `src/main/java/globe/world/mixin/DensityFunctionsShiftAMixin.java`
- `src/main/java/globe/world/mixin/DensityFunctionsShiftBMixin.java`
- `src/main/java/globe/world/mixin/DensityFunctionsShiftedNoiseMixin.java`
- `src/main/java/globe/world/mixin/DensityFunctionsWeirdScaledSamplerMixin.java`
- `src/main/java/globe/world/mixin/BlendedNoiseMixin.java`
- `src/main/java/globe/world/mixin/SurfaceSystemMixin.java`
- `src/main/java/globe/world/mixin/NoiseBasedChunkGeneratorMixin.java`
- `src/main/java/globe/world/mixin/WorldGenRegionMixin.java`
- `src/main/java/globe/world/mixin/LevelChunkPostProcessMixin.java`
- `src/main/java/globe/world/mixin/RandomSpreadStructurePlacementMixin.java`
- `src/main/java/globe/world/mixin/StructureGenerationContextMixin.java`
- `src/main/java/globe/world/mixin/StructurePlacementMixin.java`
- `src/main/java/globe/world/mixin/StructureStartMixin.java`

## Related Plans

- [Seamless Wrapping Plan](../../plans/seamless-wrapping-plan.md)
- [Terrain Periodicity Investigation](../../plans/terrain-periodicity-investigation.md)

## Related Vanilla Mechanics

- [Vanilla world generation](../../vanilla-mechanics/world-generation.md)
- [Vanilla structure edge generation](../../vanilla-mechanics/structure-edge-generation.md)

## Open Audits

- Validate periodicity for Nether terrain/noise and features.
- Audit structure query and persistence paths.
- Add a controlled virtual feature-origin pass for edge features such as monster
  rooms, or allow selected alias feature decoration to spill wrapped writes.
- Verify tiny-tile terrain is presented as stylized rather than vanilla-identical.
