# World Generation

World generation is a chunk-status pipeline driven by `ChunkGenerator`, biome sources, random state, structures, carvers, surface building, features, lighting, spawning, and final `LevelChunk` promotion.

## Key Source Files

Common sources jar:

- `net/minecraft/world/level/chunk/status/ChunkStatus.java`
- `net/minecraft/world/level/chunk/ChunkGenerator.java`
- `net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java`
- `net/minecraft/world/level/levelgen/RandomState.java`
- `net/minecraft/world/level/levelgen/DensityFunction.java`
- `net/minecraft/world/level/biome/BiomeSource.java`
- `net/minecraft/world/level/biome/Climate.java`
- `net/minecraft/world/level/StructureManager.java`
- `net/minecraft/world/level/levelgen/structure/Structure.java`
- `net/minecraft/world/level/levelgen/structure/placement/StructurePlacement.java`
- `net/minecraft/server/level/WorldGenRegion.java`

## Chunk Status Pipeline

`ChunkStatus` defines the ordered pipeline:

- `ChunkStatus.java:21` `EMPTY`
- `ChunkStatus.java:22` `STRUCTURE_STARTS`
- `ChunkStatus.java:24` `BIOMES`
- `ChunkStatus.java:25` `NOISE`
- `ChunkStatus.java:27` `CARVERS`
- `ChunkStatus.java:28` `FEATURES`
- `ChunkStatus.java:29` `INITIALIZE_LIGHT`
- `ChunkStatus.java:30` `LIGHT`
- `ChunkStatus.java:31` `SPAWN`
- `ChunkStatus.java:32` `FULL`

## Generator Entry Points

`ChunkGenerator` owns most high-level generation phases.

Important anchors:

- `ChunkGenerator.java:83` class
- `ChunkGenerator.java:85` `biomeSource`
- `ChunkGenerator.java:96` feature sorting by biome
- `ChunkGenerator.java:109` `createState`
- `ChunkGenerator.java:117` `createBiomes`
- `ChunkGenerator.java:126` `applyCarvers`
- `ChunkGenerator.java:318` `applyBiomeDecoration`
- `ChunkGenerator.java:424` `buildSurface`
- `ChunkGenerator.java:426` `spawnOriginalMobs`
- `ChunkGenerator.java:433` `getBiomeSource`
- `ChunkGenerator.java:439` mob spawn settings by biome/structure
- `ChunkGenerator.java:465` `createStructures`
- `ChunkGenerator.java:554` `tryGenerateStructure`
- `ChunkGenerator.java:599` `createReferences`
- `ChunkGenerator.java:633` `fillFromNoise`

## Structures

Structures depend on placement rules, structure set state, chunk coordinates, biome predicates, and reference accounting.

Important anchors:

- `ChunkGenerator.java:131` `findNearestMapStructure`
- `ChunkGenerator.java:270` random spread structure candidate chunk
- `ChunkGenerator.java:283` `getStructureGeneratingAt`
- `ChunkGenerator.java:291` structure loop
- `ChunkGenerator.java:298` reads `STRUCTURE_STARTS`
- `ChunkGenerator.java:480` structure placement
- `ChunkGenerator.java:490` `isStructureChunk`
- `ChunkGenerator.java:568` allowed biome set
- `ChunkGenerator.java:585` records generated start
- `ChunkGenerator.java:612` scans nearby starts for references

## Biomes, Noise, And Features

Biome selection and noise sampling are position-sensitive.

Important anchors:

- `ChunkGenerator.java:117` `createBiomes`
- `ChunkGenerator.java:121` `fillBiomesFromNoise`
- `ChunkGenerator.java:326` features per step
- `ChunkGenerator.java:331` nearby chunks used during decoration
- `ChunkGenerator.java:368` possible biomes
- `ChunkGenerator.java:386` placed feature execution
- `ServerLevel.java:321` `getUncachedNoiseBiome`

## Audit Questions

- Which phases must be periodic or topology-aware: biome sampling, noise, structures, features, or all of them?
- Do structure placements use world chunk coordinates directly?
- Do references between neighboring chunks cross the intended boundary?
- Does feature placement read neighboring chunks or blocks?
- Are worldgen random seeds derived from absolute chunk/block coordinates?
- Should generated storage be unique while visible terrain repeats?

## Globe World Notes

Alias generation must not run mutable generation phases, and canonical generation must not write spillover blocks into alias chunks.

### Alias Post-Processing

- `LevelChunk.postProcessGeneration(...)` walks queued post-processing offsets for a `LevelChunk`.
- For each queued position, it may call fluid/block ticks or `level.setBlock(pos, blockStateNew, 276)` after neighbor-shape updates.
- If the `LevelChunk` is an alias, those positions are alias coordinates. Server `Level.setBlock(...)` canonicalizes the mutation position, so alias terrain fixes can overwrite canonical terrain.

Observed symptom:

- An all-ocean canonical tile received `minecraft:grass_block` writes over water/air.
- Debug logs showed `caller=net.minecraft.world.level.chunk.LevelChunk#postProcessGeneration:596` with non-canonical original positions.

Project hook:

- `src/main/java/globe/world/mixin/LevelChunkPostProcessMixin.java:20` cancels `LevelChunk.postProcessGeneration(...)` for non-canonical chunks and clears queued post-processing offsets.

### Alias Feature Decoration

`ChunkGenerator.applyBiomeDecoration(...)` places trees, vegetation, ores, leaf litter, and structures for the center chunk. These features can write up to `ChunkStep.blockStateWriteRadius()` chunks away from the center through `WorldGenRegion.setBlock(...)`.

For Globe World, non-canonical chunks are views. Letting an alias run feature decoration creates extra, non-canonical feature centers and duplicate tree/foliage attempts that do not belong to the finite tile.

Project hook:

- `src/main/java/globe/world/mixin/ChunkGeneratorMixin.java:23` cancels biome decoration for non-canonical chunks.

### Canonical Feature Spillover

Vanilla tree/foliage features centered in a canonical edge chunk can place blocks just outside that chunk. In a 1-chunk tile, logs showed canonical center `[0,0]` writing blocks at positions such as `x=16` or `z=16` into raw target chunks `[1,0]`, `[0,1]`, or `[1,1]`; all of those wrap back to canonical chunk `[0,0]`.

Observed symptom:

- Live client aliases were missing trunks/leaves or had invisible foliage collision.
- Reload fixed the view because saved/reloaded canonical access collapsed the data back through wrapping.
- The decisive logs were `GW_WORLDGEN_WRITE ... center=[0,0] target=[1,0] wrap=(0,0) ... targetReady=false canonicalReady=false`, proving the problem happened before packet send, not as a late client update.

Current project hooks:

- `src/main/java/globe/world/mixin/WorldGenRegionMixin.java` canonicalizes `WorldGenRegion` block read/write positions for `getBlockState`, `getFluidState`, `getBlockEntity`, and `setBlock`.
- `src/main/java/globe/world/mixin/WorldGenRegionMixin.java` also applies toroidal chunk distance in `ensureCanWrite(...)`; in a 6-chunk tile, canonical chunks `2` and `-3` are adjacent across the tile seam even though vanilla's raw distance is 5.
- `src/main/java/globe/world/mixin/BulkSectionAccessMixin.java` canonicalizes `BulkSectionAccess.getSection(...)` because vanilla ore placement calls `WorldGenLevel.ensureCanWrite(...)` and then writes directly through a chunk section instead of going through `WorldGenRegion.setBlock(...)`.

Why read/write symmetry matters:

- Write-only wrapping makes features place blocks into canonical storage but still make placement decisions from unwrapped alias/protochunk state.
- That mismatch can reduce successful tree placement, miscompute leaf distances, or leave partial/floating feature remnants.
- Generation code that reads and writes through `WorldGenRegion` should see one coherent canonical coordinate space.

### Structures

Current structure hook:

- `src/main/java/globe/world/mixin/ChunkGeneratorMixin.java:34` cancels structure starts for non-canonical chunks.
- `src/main/java/globe/world/mixin/ChunkGeneratorMixin.java:49` cancels structure references for non-canonical chunks.

### Current Status And Plan

- Good: alias chunk post-processing no longer writes neighbor-shape fixes into canonical chunks.
- Good: alias chunks no longer run biome decoration, structure starts, or structure references.
- Good: `WorldGenRegion` reads/writes now use canonical block positions for direct block/fluid/entity lookups, toroidal write-radius checks, `setBlock`, and queued postprocessing positions.
- Good: ore placement's `BulkSectionAccess` path now resolves sections from canonical positions after `ensureCanWrite(...)` accepts a wrapped write.
- Good: alias chunk packets are only allowed to serialize canonical chunk data; if the canonical source is unavailable, `PlayerChunkSenderMixin` requeues the alias send instead of falling back to alias-local terrain.
- Needs testing: fresh 1-chunk and 2-chunk tile worlds with trees near all four edges and corners.
- Needs audit: worldgen APIs that bypass `WorldGenRegion.getBlockState` / `setBlock`, direct `ChunkAccess.setBlockState` calls, tick scheduling in `WorldGenRegion`, carvers, surface building, and noise/biome sampling.
- Planned: make biome/noise/feature placement periodic by wrapping coordinate inputs at the generator/biome-source/noise layer, not only by wrapping block mutations after features choose positions.
- Planned: remove or gate noisy chunk/client logs before packaging.
