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
