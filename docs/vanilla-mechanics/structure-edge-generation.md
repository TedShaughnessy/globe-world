# Structure Edge Generation

This note documents why villages and other vanilla structures can cut off at the canonical tile boundary, what the current high-level fix preserves, and what still needs validation.

The short version: structure generation is not just block placement. Vanilla splits structures into placement decisions, saved starts, saved references, piece bounding boxes, and later per-chunk placement. Globe World must carry the coordinate-frame identity needed to say "place the east-side virtual slice of this village into the west edge of the tile."

## Vanilla Structure Pipeline

Vanilla structures pass through three important phases.

### 1. Structure Starts

`ChunkGenerator.createStructures(...)` runs during `STRUCTURE_STARTS`.

Source anchors:

- `net/minecraft/world/level/chunk/ChunkGenerator.java:465` enters `createStructures(...)`.
- `ChunkGenerator.java:474` gets the center chunk position.
- `ChunkGenerator.java:490` asks `StructurePlacement.isStructureChunk(...)`.
- `ChunkGenerator.java:570` calls `Structure.generate(...)` with the selected `sourceChunkPos`.
- `ChunkGenerator.java:585` stores the resulting `StructureStart` on the center chunk.
- `net/minecraft/world/level/levelgen/structure/Structure.java:83` builds a `Structure.GenerationContext` from `sourceChunkPos`.
- `Structure.java:104` creates `new StructureStart(this, sourceChunkPos, references, builder.build())`.
- `Structure.java:238` seeds the structure random from `chunkPos.x()` and `chunkPos.z()`.

For jigsaw structures such as villages:

- `net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java:146` uses the generation context chunk.
- `JigsawStructure.java:149` starts the village at `chunkPos.getMinBlockX()` and `chunkPos.getMinBlockZ()`.
- `net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java:95` creates the center `PoolElementStructurePiece`.
- `JigsawPlacement.java:421` creates child pieces with absolute positions and absolute bounding boxes.

Important consequence: a `StructureStart` is tied to one start chunk and contains pieces whose bounding boxes are already absolute world coordinates.

### 2. Structure References

`ChunkGenerator.createReferences(...)` runs during `STRUCTURE_REFERENCES`.

Source anchors:

- `ChunkGenerator.java:599` enters `createReferences(...)`.
- `ChunkGenerator.java:608` scans source chunks in a radius around the target chunk.
- `ChunkGenerator.java:612` reads each source chunk's stored starts.
- `ChunkGenerator.java:614` checks whether the start bounding box intersects the target chunk's 16x16 block area.
- `ChunkGenerator.java:615` records a packed source chunk key as the reference.

Important consequence: references do not store the piece data. They store keys pointing back to start chunks.

### 3. Decoration Placement

Structures are actually written during biome decoration.

Source anchors:

- `ChunkGenerator.java:318` enters `applyBiomeDecoration(...)`.
- `ChunkGenerator.java:353` asks `StructureManager.startsForStructure(sectionPos, structure)` for starts referenced by the target chunk.
- `ChunkGenerator.java:354` calls `StructureStart.placeInChunk(...)` with `getWritableArea(chunk)` and the target `centerPos`.
- `ChunkGenerator.java:414` builds the target chunk writable area from the target chunk's absolute block coordinates.
- `net/minecraft/world/level/StructureManager.java:65` reads the target chunk's references.
- `StructureManager.java:72` iterates each packed reference key.
- `StructureManager.java:77` unpacks the key into a source `SectionPos`.
- `StructureManager.java:78` loads the source chunk at `STRUCTURE_STARTS`.
- `net/minecraft/world/level/levelgen/structure/StructureStart.java:81` places one start in one target chunk.
- `StructureStart.java:96` only processes pieces whose absolute bounding box intersects the supplied chunk bounding box.
- `net/minecraft/world/level/levelgen/structure/StructurePiece.java:179` places a block through `placeBlock(...)`.
- `StructurePiece.java:181` only writes if the generated absolute position is inside the supplied chunk bounding box.
- `StructurePiece.java:191` writes through `WorldGenLevel.setBlock(...)`.
- `StructurePiece.java:194` may schedule fluid ticks.
- `StructurePiece.java:198` may mark post-processing positions.

Important consequence: even if a target chunk has the right reference, placement still depends on the start's absolute piece boxes intersecting the target chunk's absolute writable box.

## Why A Finite Toroidal Tile Breaks This

Suppose the canonical tile is six chunks wide, so chunk `2` and chunk `-3` are adjacent across the east/west seam. A village start owned near chunk `2` can have pieces extending into virtual chunk `3`, which wraps to canonical chunk `-3`.

Vanilla sees those as far apart:

- The village's stored pieces are near chunk `2` / `3` in absolute coordinates.
- The canonical west-edge target chunk is chunk `-3`.
- The start bounding box near `2` / `3` does not intersect chunk `-3` in vanilla Euclidean coordinates.
- Therefore vanilla reference creation does not add the start to chunk `-3`.
- If a reference is forced in, vanilla placement still compares the unshifted piece boxes against chunk `-3`'s unshifted writable box and skips pieces that only intersect the virtual chunk `3` copy.

For Globe World, the needed identity is not just "this structure exists." It is "this structure is being viewed as a copy shifted by one whole tile in X/Z for this target chunk."

That period shift must survive:

- reference creation,
- reference key lookup,
- start retrieval,
- piece/chunk bounding-box intersection,
- block reads,
- block writes,
- block entities,
- scheduled ticks,
- post-processing marks,
- any `afterPlace(...)` structure cleanup.

## Current Implementation

The implementation preserves the virtual source coordinate through reference creation, lookup, and placement.

### `ChunkGeneratorMixin`

Project anchors:

- `mod-fabric/src/main/java/globe/world/mixin/ChunkGeneratorMixin.java:27` clears queued structure placement shifts and cancels biome decoration for non-canonical chunks.
- `ChunkGeneratorMixin.java:58` intercepts `createReferences(...)`.
- `ChunkGeneratorMixin.java:65` clears references, but not starts, for non-canonical chunks during reference generation.
- `ChunkGeneratorMixin.java:70` replaces canonical reference creation with `addToroidalStructureReferences(...)`.
- `ChunkGeneratorMixin.java:74` wraps the biome-decoration lookup from `StructureManager.startsForStructure(...)`.
- `ChunkGeneratorMixin.java:98` reads virtual reference keys and resolves them back to starts.
- `ChunkGeneratorMixin.java:110` queues the exact chunk shift for the corresponding placement call.
- `ChunkGeneratorMixin.java:131` scans nearby virtual source coordinates.
- `ChunkGeneratorMixin.java:133` asks the bounded `WorldGenRegion` for each virtual source chunk at `STRUCTURE_STARTS`.
- `ChunkGeneratorMixin.java:134` computes the shift from requested virtual source coordinates to the returned chunk's actual coordinates.
- `ChunkGeneratorMixin.java:139` tests a shifted start bounding box against the canonical target chunk.
- `ChunkGeneratorMixin.java:143` records `ChunkPos.pack(sourceX, sourceZ)` as the virtual reference key.

The important part is that the shifted intersection test no longer discards the source coordinate that made the intersection true. The stored reference key is virtual, so placement can recover the tile shift later.

### `StructurePlacementMixin`

Project anchors:

- `mod-fabric/src/main/java/globe/world/mixin/StructurePlacementMixin.java:15` intercepts `StructurePlacement.isStructureChunk(...)`.
- `StructurePlacementMixin.java:17` wraps source chunk X/Z.
- `StructurePlacementMixin.java:20` delegates alias placement checks to the wrapped canonical chunk.
- `StructurePlacementMixin.java:24` through `StructurePlacementMixin.java:94` wrap several placement probability seed inputs.
- `mod-fabric/src/main/java/globe/world/mixin/StructureGenerationContextMixin.java` wraps the structure generation random seed to canonical chunk coordinates.

Alias structure starts now keep their virtual start position, but their placement decision and structure-generation random seed are canonicalized. The goal is a transient virtual copy with the same layout as the canonical start, translated by whole tile periods.

### `StructureStartMixin`

Project anchors:

- `mod-fabric/src/main/java/globe/world/mixin/StructureStartMixin.java:19` intercepts `StructureStart.placeInChunk(...)`.
- `StructureStartMixin.java:31` consumes the queued shift for this start occurrence.
- `StructureStartMixin.java:36` moves the chunk bounding box by the exact stored whole-tile shift.
- `StructureStartMixin.java:37` moves the placement chunk position by the same shift.
- `StructureStartMixin.java:41` recursively calls vanilla `placeInChunk(...)` in the shifted coordinate frame.

This replaces the old guessed shift with the shift recovered from the virtual reference key.

### `WorldGenRegionMixin` And Spillover

Project anchors:

- `mod-fabric/src/main/java/globe/world/mixin/WorldGenRegionMixin.java:37` wraps `getBlockState(...)`.
- `WorldGenRegionMixin.java:42` virtualizes worldgen chunk lookup inside the bounded `WorldGenRegion` cache.
- `WorldGenRegionMixin.java:70` wraps `getFluidState(...)`.
- `WorldGenRegionMixin.java:75` wraps `getBlockEntity(...)`.
- `WorldGenRegionMixin.java:80` replaces `ensureCanWrite(...)` with toroidal write-radius logic.
- `WorldGenRegionMixin.java:128` wraps `setBlock(...)` and queues spillover writes.
- `WorldGenRegionMixin.java:148` wraps post-processing positions.
- `mod-fabric/src/main/java/globe/world/util/WorldGenSpillover.java:23` queues wrapped writes.
- `WorldGenSpillover.java:37` applies queued writes to canonical chunks.

These hooks are useful for feature/tree spillover and for final structure block writes. They do not solve structure identity by themselves; the reference-key and placement-shift hooks above decide whether vanilla reaches the write path.

## Failure Mode Addressed

The vanilla path mixes three coordinate frames:

1. Canonical chunk ownership: only canonical chunks should own saved mutable state.
2. Virtual source coordinates: seam-crossing structure references need to remember which whole-tile copy made a start intersect the target chunk.
3. Vanilla absolute piece coordinates: `StructureStart` and `StructurePiece` store unwrapped absolute bounding boxes and positions.

The implemented fix carries frame 2 through the pipeline.

`ChunkGeneratorMixin.addToroidalStructureReferences(...)` detects a shifted intersection and stores the virtual source key. `ChunkGeneratorMixin.startsForToroidalStructure(...)` resolves that key and queues the resulting shift. `StructureStartMixin` consumes the shift and calls vanilla placement with the target chunk box moved into the same coordinate frame as the referenced start.

The remaining risks are cases where:

- the structure is large relative to the tile,
- alias start generation still differs from the canonical translated start through an unwrapped random, biome, or height path,
- placement code performs reads, height checks, block entity writes, scheduled ticks, or `afterPlace(...)` behavior outside the block-state path already wrapped by `WorldGenRegionMixin`.

This is why the behavior should still be validated in-game as a structure-identity fix first and a block-write wrapping fix second.

## Constraints

Keep these constraints in mind for the next fix.

- Alias chunks must not become durable owners of structure starts or references.
- Canonical chunks should remain the only saved owners of mutable structure state.
- Reference generation must stay inside the bounded `WorldGenRegion` cache. Do not call live `ServerLevel.getChunk(...)` from structure-reference generation; a previous attempt stalled generation near the border and hung on quit.
- Any solution must preserve the period shift from reference scan through placement.
- Block-state wrapping is insufficient. Audit block entities, scheduled ticks, post-processing, processors, terrain adaptation, and `Structure.afterPlace(...)`.

## Design

The chosen design stores virtual reference keys, then resolves those keys to visible starts with an explicit tile-period shift during placement.

This keeps the fix high-level. Instead of rewriting every `StructurePiece`, copying jigsaw pieces, or trying to intercept every individual block edit after the fact, Globe World should preserve the missing structure-level fact:

> This target chunk references this structure start as the copy shifted by N whole tiles in X/Z.

The placement path can then stay close to vanilla:

- Canonical chunks remain the intended durable owners of structure state.
- Alias starts are allowed as transient generation products so the bounded `WorldGenRegion` cache can see virtual starts near an edge without loading far-away canonical chunks.
- `STRUCTURE_REFERENCES` may store virtual source chunk keys, because those keys describe which tile copy caused the target chunk to reference the start.
- The biome-decoration hook resolves a virtual reference key to a start visible in the bounded worldgen region.
- That same hook computes the exact chunk/block shift from the virtual reference key to the start's own chunk position.
- `StructureStart.placeInChunk(...)` receives that exact shift, rather than guessing from bounding-box centers.
- Vanilla structure pieces still run their normal `postProcess(...)` logic.
- `WorldGenRegion` wrapping and spillover remain responsible for routing the resulting block/tick/post-processing writes into canonical storage.

In other words: references carry identity, placement consumes identity, block-write wrappers handle storage.

### Why This Over Pure Option A

Pure transient virtual starts would also be correct, but it risks becoming a low-level copy/translation layer for final vanilla objects:

- `StructureStart` is final.
- Pieces store absolute bounding boxes.
- Some piece internals, cached bounds, jigsaw junctions, and processors can be awkward to clone or translate safely.

The virtual-reference-key design avoids most of that. It does not need to create a new translated structure object up front. It only needs to make the existing vanilla placement call happen in the right coordinate frame.

### Why This Over Write Interception Alone

Intercepting `setBlock(...)` only helps after vanilla has decided to place a block. The structure cutoff often happens earlier, when vanilla decides a referenced structure piece does not intersect the target chunk's unshifted bounding box. The selected design fixes that earlier decision by preserving the virtual tile shift through the reference and placement phases.

## Implementation Shape

### 1. Reference Creation Stores The Virtual Source Key

In `ChunkGeneratorMixin.addToroidalStructureReferences(...)`, when a shifted start bounding box intersects the target chunk, store the virtual source chunk key that made the intersection true.

Behavior:

- Detects `shiftedBounds.intersects(...)`.
- Stores `ChunkPos.pack(virtualSourceX, virtualSourceZ)`.
- Keeps enough information to recover `virtualSource - sourceStartChunk`.

### 2. Reference Lookup Resolves Virtual Key To A Start And Shift

`StructureManager.fillStartsForStructure(...)` currently unpacks the reference key and loads that exact chunk at `STRUCTURE_STARTS`.

For Globe World, `ChunkGeneratorMixin.startsForToroidalStructure(...)`:

- unpacks the possibly virtual reference key,
- loads the visible source chunk through the bounded `WorldGenRegion`/level path already in use,
- reads the start from that source chunk,
- computes the whole-tile chunk shift from virtual key to the start's own chunk position,
- associates that shift with this specific returned start occurrence.

This is the point where the design differs from vanilla. Vanilla returns only a `StructureStart`; Globe World also needs a placement shift for that occurrence.

### 3. Placement Uses The Stored Shift

When `StructureStart.placeInChunk(...)` is called during biome decoration, it looks up the shift associated with this target chunk/start occurrence and calls vanilla placement with:

- `chunkBB` moved by the exact whole-tile block shift,
- `chunkPos` moved by the exact whole-tile chunk shift.

That allows vanilla's existing checks to work:

- `StructureStart.placeInChunk(...)` sees shifted piece boxes intersecting the shifted chunk box.
- `StructurePiece.placeBlock(...)` sees generated positions inside the shifted chunk box.
- The eventual `WorldGenLevel.setBlock(...)` call writes virtual positions.
- `WorldGenRegionMixin` wraps those positions into canonical storage.

### 4. Saved Data Policy

Canonical chunks should remain the only intended saved owners of starts. Alias chunks currently need transient starts for bounded worldgen placement, so persistence of alias starts still needs an explicit save/load audit.

Virtual reference keys are acceptable only if all reference readers understand how to resolve them:

- During generation placement, they must resolve to starts plus a shift.
- During later structure queries, mob spawn override checks, or locate-style reads, they must not cause duplicate persistent starts or raw alias chunk loads.

If that becomes too invasive, use a transient side table for generation-time shifts and normalize saved references back to canonical keys. That is less crash/restart robust for half-generated chunks, so persisted virtual reference keys are preferable if the read path can be made consistent.

### 5. Follow-Up Cleanup

After broader validation:

- Keep alias `createStructures(...)` deterministic by canonicalizing placement decisions and generation random seeds while preserving virtual start positions.
- Audit alias chunk saving so transient alias starts do not become durable independent structure state.
- Keep `WorldGenRegion` write/read wrapping, because it is still needed after placement reaches block writes.

## Alternatives Considered

### Option A: Transient Virtual Structure Starts

Keep saved starts canonical, but create transient translated views during reference lookup and placement.

Shape:

- During reference creation, record enough information to identify the canonical start plus its tile-period shift for the target chunk.
- During `StructureManager.startsForStructure(...)`, resolve that reference into a transient virtual start.
- The virtual start should present shifted bounding boxes, shifted pieces, shifted start chunk position, and shifted reference position for intersection and placement only.
- Writes still flow through `WorldGenRegion` and wrap back to canonical storage.

Pros:

- Matches vanilla's start/reference/place architecture.
- Keeps saved ownership canonical.
- Makes the period shift explicit instead of inferred.

Cons:

- Vanilla `StructureStart` and many piece fields are private/final or mutable in awkward ways.
- A robust implementation may need wrapper interfaces, copied piece containers, accessor mixins, or a custom placement path instead of direct subclassing.
- Need careful handling for cached bounding boxes and jigsaw junctions.

### Option B: Store Virtual Reference Keys, Then Resolve To Canonical Starts With Shift

Change toroidal reference creation to store the virtual source key that caused the intersection, not the canonical start key. For example, when requested source chunk `-4` resolves to canonical chunk `2`, store `ChunkPos.pack(-4, z)` as the reference.

Then teach the `StructureManager`/worldgen lookup path:

- unpack virtual key,
- resolve its canonical owner,
- compute `shift = virtualKey - canonicalStart.getChunkPos()`,
- place a shifted/transient start.

Pros:

- The existing reference `LongSet` can carry a virtual coordinate without adding a side table.
- The period shift survives past reference creation.

Cons:

- Vanilla `StructureManager.fillStartsForStructure(...)` currently throws the key away after loading the source chunk.
- Returning the canonical start alone is not enough; the shift still needs to be attached to placement somehow.
- Saved chunk data should not persist arbitrary alias references unless they are normalized or treated as transient generation-only data.

### Option C: Direct Edge Placement Pass

Avoid saved virtual references. During canonical edge chunk decoration, scan/generate candidate virtual starts close enough to intersect the target chunk and place only the wrapped slice.

Shape:

- For each canonical target chunk near a tile edge, iterate virtual source chunks within `MAX_STRUCTURE_DISTANCE`.
- Resolve/generate the canonical start or deterministic virtual start for that source.
- Test shifted bounds against the target chunk.
- Call a custom placement helper with the exact shift.
- Do not store alias starts/references.

Pros:

- Can be local to generation of canonical chunks.
- Avoids teaching saved reference data about virtual aliases.
- Easier to keep alias chunks as non-owners.

Cons:

- Risks duplicating vanilla structure selection logic.
- Needs deterministic generation without causing chunk loads.
- Must avoid placing the same slice twice.
- More invasive for multiple structure placement types and exclusion zones.

### Option D: Expand Canonical Starts To Toroidal Piece Coordinates

At `STRUCTURE_STARTS`, generate canonical starts in a coordinate frame chosen so pieces may intentionally extend outside the canonical tile. Reference creation and placement would then explicitly consider shifted copies of each canonical start.

Pros:

- Canonical starts remain the source of truth.
- Could make seam-crossing starts deterministic and saveable.

Cons:

- Still needs virtual reference/placement shifts.
- Saved piece coordinates outside the canonical tile may affect locate commands, mob spawn overrides, terrain adaptation, and serialization.
- Requires careful compatibility with vanilla assumptions that a start's chunk position and piece positions live in the same ordinary world coordinate frame.

## Validation Checklist

The selected path is virtual reference keys with explicit placement-shift propagation. Validate:

1. Villages crossing east, west, north, and south tile edges.
2. Villages crossing tile corners.
3. Structure block entities, scheduled ticks, post-processing, terrain adaptation, and `afterPlace(...)`.
4. Save/reload behavior for chunks that generated virtual alias starts or virtual reference keys.
5. Later structure queries such as spawn overrides and locate-style reads.

The important invariant for the real fix:

> If a target canonical chunk references a seam-crossing structure, the placement call must know the exact tile-period shift that made that structure intersect the target chunk.
