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

