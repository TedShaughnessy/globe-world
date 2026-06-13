# Worldgen Direct Mutation Matrix

This matrix records the Minecraft 26.1.2 worldgen paths that can write chunk
state directly or persist structure data. It supports the Globe World policy in
`docs/mod-mechanics/worldgen.md`: canonical chunks own durable state; alias
chunks are views or transient generation frames.

Source references use jar-internal paths in the common sources jar:

`.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-52430b475d/26.1.2/minecraft-common-52430b475d-26.1.2-sources.jar`

## Coverage Status

| Source | Phase | Mutation | Current Globe coverage | Risk | Action |
| --- | --- | --- | --- | --- | --- |
| `net/minecraft/server/level/WorldGenRegion.java`, `setBlock(...)` | Features, structures, decoration | `ChunkAccess.setBlockState(...)`, POI updates, block entity dummy tags, post-processing marks | Covered by `WorldGenRegionMixin` and `GenerationWindow`; wrapped writes are canonicalized, visible seam writes keep vanilla side effects, and spillover queues block-state replay | Low for vanilla visible writes; medium for unobserved external-provider writes | Keep current hook. Do not replay richer side effects unless a vanilla repro proves block-state-only spillover is insufficient. |
| `net/minecraft/server/level/WorldGenRegion.java`, `getBlockState(...)`, `getFluidState(...)`, `getBlockEntity(...)`, `getChunk(...)`, `hasChunk(...)`, `ensureCanWrite(...)` | Features, structures, decoration validation | Read and write classification against bounded region chunks | Covered by `WorldGenRegionMixin` and `GenerationWindow` | Low | Keep read/write symmetry through the region helper. |
| `net/minecraft/world/level/chunk/ChunkAccess.java`, `setBlockState(...)` | Shared low-level chunk mutation | Abstract state write into a chunk owner | Covered only through callers; not globally hooked | Medium if a caller writes an alias chunk directly | Do not add a broad hook. Classify by caller so same-chunk terrain fill remains fast and explicit. |
| `net/minecraft/world/level/chunk/LevelChunkSection.java`, `setBlockState(...)` | Direct section writes | Palette mutation plus section block/fluid/random-tick counters | Covered by caller boundaries: same-chunk terrain fill is canonical; ore uses `BulkSectionAccessMixin` | Low for vanilla; medium for unknown providers | Keep `BulkSectionAccess` hook as the narrow section escape hatch. |
| `net/minecraft/world/level/chunk/BulkSectionAccess.java`, `getSection(...)` | Ore placement | Acquires a section from `LevelAccessor.getChunk(...)`; `OreFeature` then writes the section directly | Covered by `BulkSectionAccessMixin` | Low | Keep hook. This is the known vanilla direct-section bypass of `WorldGenRegion.setBlock(...)`. |
| `net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java`, `doFill(...)` | `NOISE` | Direct `LevelChunkSection.setBlockState(...)`, heightmap updates, aquifer post-processing marks | Canonical chunk only | Low | No hook. The phase fills the center protochunk; alias generation is skipped by the chunk/status ownership model. |
| `net/minecraft/world/level/levelgen/SurfaceSystem.java`, `buildSurface(...)` | `SURFACE` / terrain shaping | `protoChunk.setBlockState(...)` through a local `BlockColumn`, fluid post-processing marks | Canonical chunk only; surface/noise coordinate sampling covered separately by terrain hooks | Low | No direct-mutation hook. Keep terrain periodicity hooks separate from mutation ownership. |
| `net/minecraft/world/level/levelgen/carver/WorldCarver.java`, `carveBlock(...)` | `CARVERS` | `chunk.setBlockState(...)`, fluid post-processing marks, top-material repair | Canonical chunk only; carver source chunks may be virtualized through `WorldGenRegion.getChunk(...)` in `NoiseBasedChunkGenerator.applyCarvers(...)` | Low | No new hook. Carvers carve the target canonical chunk. |
| `net/minecraft/world/level/levelgen/feature/OreFeature.java`, `doPlace(...)` | `FEATURES` | Direct `LevelChunkSection.setBlockState(...)` after `level.ensureCanWrite(...)` | Covered by `GenerationWindow` plus `BulkSectionAccessMixin` | Low | Keep hook and document as the primary vanilla section bypass. |
| `net/minecraft/world/level/levelgen/feature/Feature.java`, `setBlock(...)`, `safeSetBlock(...)`, `markAboveForPostProcessing(...)` | `FEATURES` | `LevelWriter.setBlock(...)`, direct post-processing marks on `level.getChunk(pos)` | Block writes covered by `WorldGenRegionMixin`; post-processing positions are canonicalized in `WorldGenRegionMixin.markPosForPostprocessing(...)` when routed through region methods | Low for visible writes; medium for unobserved spillover | Keep current behavior. Alias-origin-only feature centers remain outside this direct-mutation audit. |
| `net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java` | `FEATURES` | Chests/spawner blocks plus loot table and spawner block entity initialization | Covered for visible wrapped writes because `WorldGenRegion.setBlock(...)` creates/loads block entities at canonical positions; unobserved spillover replays only block state | Medium if a vanilla monster room needs unobserved spillover for chest/spawner data | Document limitation. Add a richer spillover payload only after a vanilla seam reproduction. |
| `net/minecraft/world/level/levelgen/feature/EndGatewayFeature.java` | `FEATURES` | End gateway block entity exit data after block placement | Tiled dimensions currently exclude the End progression target; visible writes would use canonical `WorldGenRegion` block entity lookup | Out of scope for current tiled dimensions | No hook. Revisit only if the End becomes tiled. |
| `net/minecraft/world/level/levelgen/feature/VoidStartPlatformFeature.java` | `FEATURES` | Spawn/platform blocks through `level.setBlock(...)` | Covered by `WorldGenRegionMixin` when feature decoration runs in a tiled dimension | Low | No extra hook. |
| `net/minecraft/world/level/levelgen/BelowZeroRetrogen.java`, `replaceOldBedrock(...)`, `applyBedrockMask(...)` | Retrogen/upgrading | Same-chunk `ProtoChunk.setBlockState(...)` | Canonical chunk only; `GenerationWindow.canWriteCanonical(...)` preserves vanilla upgrade-height checks for region writes | Low | No hook. Retrogen state belongs to the chunk being upgraded. |
| `net/minecraft/world/level/chunk/ChunkAccess.java`, `setBlockEntity(...)`, `setBlockEntityNbt(...)`, `removeBlockEntity(...)` | Worldgen side effects | Block entity attachment or pending block entity NBT | Covered when `WorldGenRegion.setBlock(...)` performs the write and the destination is visible; unobserved spillover is block-state-only | Medium for unobserved provider path | Explicit limitation. Do not serialize spillover queues; do not replay block entity data unguarded. |
| `net/minecraft/server/level/WorldGenRegion.java`, `blockTicks` / `fluidTicks` via `WorldGenTickAccess` | Worldgen side effects | Scheduled block/fluid ticks in chunk tick containers | Visible writes use vanilla region/chunk ownership; unobserved spillover does not reconstruct ticks | Low for audited vanilla terrain/features; medium for unknown providers | Explicit limitation until a vanilla seam bug reproduces. |
| `net/minecraft/world/level/chunk/storage/SerializableChunkData.java`, `packStructureData(...)`, `unpackStructureStart(...)`, `unpackStructureReferences(...)` | Save/load | Saves starts and references from the current chunk; filters references more than 8 chunks away on load | Canonical chunk only for saved chunks; Globe may persist virtual reference keys on canonical target chunks to recover placement shifts, but starts remain canonical owners | Low if references stay bounded; high if alias starts are saved | Keep virtual references bounded and treat them as placement metadata, not ownership. Alias starts must not be saved as chunk owners. |
| `net/minecraft/world/level/chunk/ChunkGenerator.java`, `createStructures(...)` and `tryGenerateStructure(...)` | `STRUCTURE_STARTS` | `StructureManager.setStartForStructure(...)` writes starts to the center chunk | Covered by structure placement mixins and forced progression code; canonical starts are durable | Low | Keep canonical start ownership. Forced starts must be written through `StructureManager.setStartForStructure(...)` on canonical chunks. |
| `net/minecraft/world/level/chunk/ChunkGenerator.java`, `createReferences(...)` | `STRUCTURE_REFERENCES` | Scans nearby starts and stores reference keys in target chunk | Covered by `ChunkGeneratorMixin`; non-canonical target chunks get empty references, canonical target chunks can store virtual source keys for shifted placement | Medium if a future change stores an unbounded alias key | Keep current bounded virtual-reference contract; do not load chunks outside `WorldGenRegion`. |
| `net/minecraft/world/level/chunk/ChunkGenerator.java`, `applyBiomeDecoration(...)` and `StructureManager.startsForStructure(...)` | `FEATURES` / structure placement | Reads references, resolves starts, calls `StructureStart.placeInChunk(...)` | Covered by `ChunkGeneratorMixin`, `StructurePlacementShifts`, and `StructureStartMixin` | Low for audited structures; manual seam tests still useful | Keep transient shift queue. Structure starts remain canonical; shifts are not saved as starts. |
| `net/minecraft/world/level/chunk/ChunkGenerator.java`, `findNearestMapStructure(...)` | Locate/query | Raw locate search, optional reference creation through `tryAddReference(...)` | Raw vanilla command policy unless a specific Globe command wraps output; lower-level structure checks use placement hooks | Low for persistence; medium for user surprise | Leave `/locate` raw under command policy. Use `/globeworld` diagnostics for topology-aware progression checks. |
| `net/minecraft/world/level/StructureManager.java`, `getStructureAt(...)`, `getStructureWithPieceAt(...)`, `getAllStructuresAt(...)` | Runtime structure query | Queries references at raw chunk/block position and resolves starts | Mostly raw vanilla structure-query semantics; gameplay-specific callers should be audited separately | Medium if gameplay depends on cross-seam structure membership | Do not globally wrap. Add targeted caller hooks only for concrete gameplay regressions. |

## Audit Decision

No broad `ChunkAccess` or `LevelChunkSection` hook is justified for vanilla
Minecraft 26.1.2. The known direct mutation paths split into three groups:

- same-chunk terrain/carver/surface/retrogen writes, where the center
  protochunk is already the canonical owner;
- feature and structure writes through `WorldGenRegion`, where
  `GenerationWindow`, `WorldGenRegionMixin`, and `WorldGenSpillover` enforce
  canonical ownership;
- ore placement's direct section path, which is covered by
  `BulkSectionAccessMixin`.

Unobserved spillover remains intentionally block-state-only. Replaying block
entities, POIs, scheduled block ticks, or scheduled fluid ticks would need a
specific vanilla reproduction and guarded payload design so stale replay cannot
overwrite newer canonical generation state.

Structure starts are durable only on canonical chunks. Canonical target chunks
may store bounded virtual source reference keys so placement can recover a
whole-tile shift, but those keys are placement metadata: they do not create
alias-owned starts.
