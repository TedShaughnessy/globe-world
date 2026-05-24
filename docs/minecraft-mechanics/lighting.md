# Lighting

Lighting is split into block light and sky light engines. Server-side chunk/light state is asynchronous through `ThreadedLevelLightEngine`; packet data later feeds client-side `LevelLightEngine`.

## Key Source Files

Common sources jar:

- `net/minecraft/world/level/lighting/LevelLightEngine.java`
- `net/minecraft/server/level/ThreadedLevelLightEngine.java`
- `net/minecraft/world/level/lighting/BlockLightEngine.java`
- `net/minecraft/world/level/lighting/SkyLightEngine.java`
- `net/minecraft/world/level/lighting/LayerLightSectionStorage.java`
- `net/minecraft/world/level/chunk/LightChunkGetter.java`
- `net/minecraft/network/protocol/game/ClientboundLightUpdatePacket.java`
- `net/minecraft/network/protocol/game/ClientboundLightUpdatePacketData.java`
- `net/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket.java`

Client-only sources jar:

- `net/minecraft/client/multiplayer/ClientChunkCache.java`
- `net/minecraft/client/multiplayer/ClientPacketListener.java`

## Light Engine Entry Points

`LevelLightEngine` delegates to block and sky engines.

Important anchors:

- `LevelLightEngine.java:34` `checkBlock`
- `LevelLightEngine.java:50` `runLightUpdates`
- `LevelLightEngine.java:64` `updateSectionStatus`
- `LevelLightEngine.java:75` `setLightEnabled`
- `LevelLightEngine.java:86` `propagateLightSources`
- `LevelLightEngine.java:128` `queueSectionData`
- `LevelLightEngine.java:138` `retainData`

`ThreadedLevelLightEngine` wraps these operations as queued chunk tasks.

Important anchors:

- `ThreadedLevelLightEngine.java:53` `runLightUpdates`
- `ThreadedLevelLightEngine.java:58` `checkBlock`
- `ThreadedLevelLightEngine.java:68` `updateChunkStatus`
- `ThreadedLevelLightEngine.java:85` `updateSectionStatus`
- `ThreadedLevelLightEngine.java:96` `propagateLightSources`
- `ThreadedLevelLightEngine.java:103` `setLightEnabled`
- `ThreadedLevelLightEngine.java:113` `queueSectionData`
- `ThreadedLevelLightEngine.java:147` `initializeLight`
- `ThreadedLevelLightEngine.java:167` `lightChunk`

## Server Chunk Hooks

`ServerChunkCache` exposes lighting to the level and marks chunk holders for broadcast.

Important anchors:

- `ServerChunkCache.java:125` `getLightEngine`
- `ServerChunkCache.java:271` `getChunkForLighting`
- `ServerChunkCache.java:475` `onLightUpdate`

## Packet Data

Full chunk sends include light data:

- `ClientboundLevelChunkWithLightPacket.java:22` constructor
- `ClientboundLevelChunkWithLightPacket.java:28` reads `ChunkPos`
- `ClientboundLevelChunkWithLightPacket.java:31` chunk data
- `ClientboundLevelChunkWithLightPacket.java:32` light data
- `ClientboundLevelChunkWithLightPacket.java:59` `getX`
- `ClientboundLevelChunkWithLightPacket.java:63` `getZ`

Light update packet data is keyed by chunk position plus section indexes:

- `ClientboundLightUpdatePacketData.java:27` `ChunkPos`
- `ClientboundLightUpdatePacketData.java:39` loops light sections
- `ClientboundLightUpdatePacketData.java:69` `prepareSectionData`
- `ClientboundLightUpdatePacketData.java:77` `SectionPos.of(pos, ...)`

Client application:

- `ClientPacketListener.java:849` `handleLevelChunkWithLight`
- `ClientPacketListener.java:2284` `handleLightUpdatePacket`
- `ClientChunkCache.java:166` `onLightUpdate`

## Audit Questions

- Is a light update keyed by storage `ChunkPos`, visible `ChunkPos`, or packet `ChunkPos`?
- Does `SectionPos` need transformation independently of `BlockPos`?
- Are sky light columns continuous across the intended boundary?
- Can queued light tasks run after a chunk holder identity has changed?
- Does full chunk light data and later incremental light data use the same coordinate convention?

