# Random Ticks

Random ticks are per-tick sampling inside block-ticking chunks. They are not stored in `LevelTicks`; they happen while a `LevelChunk` is ticking.

## Key Source Files

Common sources jar:

- `net/minecraft/server/level/ServerChunkCache.java`
- `net/minecraft/server/level/ServerLevel.java`
- `net/minecraft/world/level/chunk/LevelChunk.java`
- `net/minecraft/world/level/chunk/LevelChunkSection.java`
- `net/minecraft/world/level/block/Block.java`
- `net/minecraft/world/level/block/LiquidBlock.java`

## Chunk Tick Selection

`ServerChunkCache` chooses block-ticking chunks after distance manager and chunk map processing.

Important anchors:

- `ServerChunkCache.java:320` `tick`
- `ServerChunkCache.java:340` `tickChunks`
- `ServerChunkCache.java:372` chunk ticking flow
- `ServerChunkCache.java:380` reads `RANDOM_TICK_SPEED`
- `ServerChunkCache.java:406` `forEachBlockTickingChunk`

## Random Tick Execution

`ServerLevel.tickChunk(...)` samples random block positions inside chunk sections.

Important anchors:

- `ServerLevel.java:486` `tickChunk`
- `ServerLevel.java:499` `tickBlocks`
- `ServerLevel.java:501` chunk sections
- `ServerLevel.java:511` `randomTick`
- `ServerLevel.java:514` `blockState.randomTick(...)`
- `ServerLevel.java:519` `fluidState.randomTick(...)`

`LiquidBlock` delegates random ticks to the fluid state:

- `LiquidBlock.java:109` `isRandomlyTicking`
- `LiquidBlock.java:113` `randomTick`

## Audit Questions

- Which chunk positions are considered block-ticking?
- Are random positions sampled in storage chunk coordinates or visible chunk coordinates?
- If one storage chunk is visible through multiple aliases, should it random tick once or once per visible alias?
- Do randomly ticking blocks schedule later ticks or mutate neighbors across boundaries?
- Are random-tick side effects deterministic under coordinate transformation?

## Globe World Notes

Random ticks are simulation effects, not view effects. They must run against canonical chunk data only.

Why this matters:

- Vanilla `ServerLevel.tickChunk(...)` samples random positions from `chunk.getPos()` and then calls block/fluid `randomTick(...)`.
- Grass uses random ticks in `SpreadingSnowyBlock.randomTick(...)` and can call `level.setBlockAndUpdate(...)` to turn itself into dirt.
- If an alias `LevelChunk` is ticked directly, its sampled local block can be stale or generated alias data, while later mutation goes through wrapped level/chunk access and writes to canonical storage. That can replace unrelated canonical blocks with dirt or place dirt where the canonical chunk has air.

Project hooks:

- `src/main/java/globe/world/mixin/ChunkMapRandomTickMixin.java:28` tracks the game time for the current `ServerLevel.tickChunk(...)` pass.
- `src/main/java/globe/world/mixin/ChunkMapRandomTickMixin.java:34` injects at the head of `ServerLevel.tickChunk(...)`.
- `src/main/java/globe/world/mixin/ChunkMapRandomTickMixin.java:48` clears canonical tick tracking when the server game time changes.
- `src/main/java/globe/world/mixin/ChunkMapRandomTickMixin.java:53` skips duplicate aliases of the same canonical chunk during the same game tick.
- `src/main/java/globe/world/mixin/ChunkMapRandomTickMixin.java:61`-`:70` swaps alias chunks for the canonical `LevelChunk`, calls vanilla `tickChunk(...)` once through a guarded recursive call, and cancels the alias tick.

Current status:

- Good: random block/fluid ticks run once per canonical chunk per `forEachBlockTickingChunk(...)` pass.
- Good: multiplayer aliases dedupe together because the dedupe key is the canonical `ChunkPos`.
- Good: random tick positions are sampled from canonical chunk coordinates, so block/fluid logic sees canonical `BlockPos`.
- Note: `ServerLevel.tickChunk(...)` also performs its `iceandsnow` precipitation pass before random block/fluid ticks, so this canonical chunk swap covers that lane too. Thunder is handled from the spawning chunk lane.
