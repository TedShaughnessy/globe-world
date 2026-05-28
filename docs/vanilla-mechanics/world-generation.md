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
- `StructureManager.java:41` creates a region-scoped manager for decoration
- `StructureManager.java:50` reads structure references for a chunk
- `StructureManager.java:67` resolves referenced start chunks
- `StructureStart.java:74` places only pieces intersecting the target chunk bounding box
- `StructurePiece.java:124` stores each piece's absolute bounding box
- `StructurePiece.java:167` only writes blocks inside the supplied chunk bounding box

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
- `PlacedFeature.java:39` placement modifier stream
- `InSquarePlacement.java:18` random position inside the decorated chunk
- `Feature.java:176` `safeSetBlock(...)`
- `MonsterRoomFeature.java:25` dungeon/monster-room feature
- `MonsterRoomFeature.java:46` validates the whole room envelope before writing
- `MonsterRoomFeature.java:95` places chest loot block entities
- `MonsterRoomFeature.java:101` places and initializes the spawner block entity

## Audit Questions

- Which phases must be periodic or topology-aware: biome sampling, noise, structures, features, or all of them?
- Do structure placements use world chunk coordinates directly?
- Do references between neighboring chunks cross the intended boundary?
- Does feature placement read neighboring chunks or blocks?
- Are worldgen random seeds derived from absolute chunk/block coordinates?
- Should generated storage be unique while visible terrain repeats?
- Does the object have an anchor/start outside the canonical tile whose body crosses into it?
- Does the object store secondary data outside block states, such as chest loot tables, spawner data, scheduled ticks, or post-processing offsets?

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

See [Structure Edge Generation](structure-edge-generation.md) for the detailed investigation of why villages and other structures still cut off at canonical tile boundaries.

Vanilla structures are split into two chunk statuses:

- `STRUCTURE_STARTS`: `ChunkGenerator.createStructures(...)` decides whether a start belongs to a chunk and stores a `StructureStart` on that chunk.
- `STRUCTURE_REFERENCES`: `ChunkGenerator.createReferences(...)` scans nearby starts and records references on every chunk whose 16x16 area intersects the start's bounding box.

During biome decoration, `ChunkGenerator.applyBiomeDecoration(...)` asks `StructureManager.startsForStructure(...)` for the target chunk's references, then calls `StructureStart.placeInChunk(...)`. `StructureStart` only calls `StructurePiece.postProcess(...)` for pieces whose absolute bounding boxes intersect the supplied chunk bounding box.

For Globe World, this means a village crossing the east/west seam needs all of these to become toroidal:

- The structure placement decision for a start chunk.
- The stored start identity and piece bounding boxes.
- The reference scan from target chunk to nearby start chunks.
- The `StructureManager` lookup from reference key back to the start chunk.
- The chunk bounding box passed to `StructureStart.placeInChunk(...)`.
- Block, fluid, block entity, scheduled tick, and post-processing writes performed by pieces.

Current project hooks:

- Earlier behavior canceled structure starts and references for non-canonical chunks. That prevented duplicate alias structure data, but it also removed virtual starts needed by canonical edge chunks to place the opposite side of a seam-crossing village.
- `src/main/java/globe/world/mixin/ChunkGeneratorMixin.java` currently clears alias structure references, stores virtual source keys during canonical reference creation, and queues exact placement shifts during biome decoration.
- `src/main/java/globe/world/mixin/StructurePlacementMixin.java` makes `StructurePlacement.isStructureChunk(...)` periodic for alias start generation.
- `src/main/java/globe/world/mixin/StructureGenerationContextMixin.java` canonicalizes structure-generation random seeds while keeping alias start positions virtual.
- `src/main/java/globe/world/mixin/StructureStartMixin.java` consumes the queued shift and calls vanilla placement with a shifted chunk bounding box.
- This needs in-game validation: the implementation is designed to preserve coherent virtual structure-start identity, but villages crossing all four edges/corners still need testing.

Important constraint:

- Do not call `ServerLevel.getChunk(...)` from structure-reference generation. A previous attempt did this while a chunk was generating and caused chunk loading to stall near the tile border and hang on quit. Reference scans must stay inside the bounded `WorldGenRegion.getChunk(...)` cache.

Implemented design:

- Keep canonical chunks as the intended saved structure-start owners; alias starts are transient worldgen data and still need a save/load audit.
- During structure reference generation, store virtual source reference keys so the exact whole-tile shift survives lookup.
- During structure placement, resolve each virtual reference to a start and move the chunk bounding box by the stored shift instead of guessing from bounding-box centers.
- Route all structure block writes through `WorldGenRegion`/spillover wrapping, and separately audit block entities, loot tables, scheduled ticks, and post-processing writes.

### Dungeons / Monster Rooms

Vanilla monster rooms are not structures; they are configured features (`Feature.MONSTER_ROOM`) run during biome decoration. The feature is anchored at one placed feature position and then validates/writes a small room around that origin.

Why they cut off at a tile boundary:

- `ChunkGeneratorMixin` cancels `applyBiomeDecoration(...)` for non-canonical chunks, so feature origins in alias chunks do not run.
- A dungeon whose origin is inside a canonical edge chunk can spill blocks across the boundary through `WorldGenRegion.setBlock(...)` and `WorldGenSpillover`.
- A dungeon whose origin would be just outside the canonical tile but whose room crosses into the tile is missing entirely, because the alias feature center is skipped.
- `MonsterRoomFeature` performs whole-envelope validation before writing. If its reads see inconsistent canonical/alias state, it can reject placement rather than placing a partial room.
- Chests and spawners use block entities and loot/spawner initialization after block placement. Block-state spillover alone is not enough; block entity NBT/data must also be canonicalized or replayed for wrapped positions.

Associated systems:

- `PlacedFeature` and placement modifiers choose candidate origins per decorated chunk.
- `BiomeFilterMixin` wraps biome checks, but does not create alias feature centers.
- `WorldGenRegionMixin` wraps direct block/fluid/entity lookups and writes.
- `WorldGenSpillover` replays wrapped block-state writes into canonical storage.
- Block entity creation/initialization paths such as `RandomizableContainer.setBlockEntityLootTable(...)` and `SpawnerBlockEntity.setEntityId(...)` still need an explicit audit for wrapped positions and delayed canonical replay.

Possible next design:

- For selected cross-boundary features, allow alias feature decoration to run in a transient mode that does not own persistent chunk data but can spill wrapped mutations into canonical storage.
- Alternatively, during canonical edge-chunk decoration, run extra feature-origin passes for the adjacent virtual chunks and only keep writes that wrap into canonical storage.
- Apply the same read/write symmetry rule as trees: validation reads, block writes, block entity writes, scheduled ticks, and post-processing markers must all see the same toroidal coordinate space.

### Current Status And Plan

- Good: alias chunk post-processing no longer writes neighbor-shape fixes into canonical chunks.
- Good: alias chunks no longer run biome decoration or structure references.
- Needs validation: structure starts, references, and placement now carry virtual source keys and explicit shifts, but villages still need edge/corner testing.
- Good: `WorldGenRegion` reads/writes now use canonical block positions for direct block/fluid/entity lookups, toroidal write-radius checks, `setBlock`, and queued postprocessing positions.
- Good: ore placement's `BulkSectionAccess` path now resolves sections from canonical positions after `ensureCanWrite(...)` accepts a wrapped write.
- Good: alias chunk packets are only allowed to serialize canonical chunk data; if the canonical source is unavailable, `PlayerChunkSenderMixin` requeues the alias send instead of falling back to alias-local terrain.
- Done: tree/foliage spillover now has a queued canonical replay path.
- Needs validation: villages/structures crossing tile boundaries.
- Open: dungeons/monster rooms crossing tile boundaries still cut off.
- Needs audit: worldgen APIs that bypass `WorldGenRegion.getBlockState` / `setBlock`, direct `ChunkAccess.setBlockState` calls, block entity writes, tick scheduling in `WorldGenRegion`, carvers, surface building, and noise/biome sampling.
- Planned: make biome/noise/feature placement periodic by wrapping coordinate inputs at the generator/biome-source/noise layer, not only by wrapping block mutations after features choose positions.
- Planned: remove or gate noisy chunk/client logs before packaging.
