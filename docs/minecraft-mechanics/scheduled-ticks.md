# Scheduled Ticks

Scheduled ticks are delayed block or fluid callbacks keyed by block position and stored in per-chunk tick containers. They are separate from random ticks.

## Key Source Files

Common sources jar:

- `net/minecraft/world/ticks/LevelTicks.java`
- `net/minecraft/world/ticks/LevelChunkTicks.java`
- `net/minecraft/world/ticks/ScheduledTick.java`
- `net/minecraft/world/ticks/SavedTick.java`
- `net/minecraft/world/ticks/TickContainerAccess.java`
- `net/minecraft/world/level/chunk/LevelChunk.java`
- `net/minecraft/server/level/ServerLevel.java`

## Level Tick Containers

`ServerLevel` owns two `LevelTicks` instances:

- `ServerLevel.java:211` `blockTicks`
- `ServerLevel.java:212` `fluidTicks`
- `ServerLevel.java:379` block tick processing
- `ServerLevel.java:381` fluid tick processing
- `ServerLevel.java:797` `tickFluid`
- `ServerLevel.java:805` `tickBlock`
- `ServerLevel.java:1274` `getBlockTicks`
- `ServerLevel.java:1278` `getFluidTicks`

## Per-Chunk Registration

`LevelChunk` exposes its block and fluid tick containers and registers them with the level when chunks become active.

Important anchors:

- `LevelChunk.java:190` `getBlockTicks`
- `LevelChunk.java:195` `getFluidTicks`
- `LevelChunk.java:643` `registerTickContainerInLevel`
- `LevelChunk.java:648` `unregisterTickContainerFromLevel`
- `ServerLevel.java:1841` `startTickingChunk`
- `ServerLevel.java:1001` `unload`

## Scheduling And Execution

`LevelTicks.schedule(...)` maps a `ScheduledTick` to a chunk key using the tick position.

Important anchors:

- `LevelTicks.java:50` `addContainer`
- `LevelTicks.java:61` `removeContainer`
- `LevelTicks.java:71` `schedule`
- `LevelTicks.java:72` `ChunkPos.pack(tick.pos())`
- `LevelTicks.java:81` `tick`
- `LevelTicks.java:117` `tickCheck`
- `LevelTicks.java:174` `scheduleForThisTick`
- `LevelTicks.java:186` output callback
- `LevelTicks.java:202` `hasScheduledTick`
- `LevelTicks.java:208` `willTickThisTick`

`LevelTicks` also has area operations used by commands and structure-like edits:

- `LevelTicks.java:220` area-to-section bounds
- `LevelTicks.java:237` remove ticks in area
- `LevelTicks.java:259` copy ticks from area
- `LevelTicks.java:268` reschedule copied tick with offset

## Audit Questions

- When a tick is scheduled, is its `BlockPos` in the same coordinate space as the loaded chunk container?
- What happens if `ChunkPos.pack(tick.pos())` points at an unloaded or alias chunk?
- Do copied ticks need coordinate transformation?
- Are block and fluid ticks transformed consistently?
- Can a scheduled tick fire after the visible location that created it has moved or unloaded?

