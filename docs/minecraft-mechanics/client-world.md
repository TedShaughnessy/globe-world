# Client World

The client receives server packets, stores chunks in `ClientChunkCache`, applies block/light/block-entity updates, and exposes chunk data to rendering and client-side world queries.

## Key Source Files

Client-only sources jar:

- `net/minecraft/client/multiplayer/ClientPacketListener.java`
- `net/minecraft/client/multiplayer/ClientChunkCache.java`
- `net/minecraft/client/multiplayer/ClientLevel.java`
- `net/minecraft/client/renderer/LevelRenderer.java`
- `net/minecraft/client/renderer/ViewArea.java`

Common sources jar:

- `net/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket.java`
- `net/minecraft/network/protocol/game/ClientboundForgetLevelChunkPacket.java`
- `net/minecraft/network/protocol/game/ClientboundBlockUpdatePacket.java`
- `net/minecraft/network/protocol/game/ClientboundSectionBlocksUpdatePacket.java`
- `net/minecraft/network/protocol/game/ClientboundBlockEntityDataPacket.java`
- `net/minecraft/network/protocol/game/ClientboundLightUpdatePacket.java`

## Chunk Packet Application

`ClientPacketListener` handles full chunk data and chunk unloads.

Important anchors:

- `ClientPacketListener.java:849` `handleLevelChunkWithLight`
- `ClientPacketListener.java:888` `replaceWithPacketData`
- `ClientPacketListener.java:905` `handleForgetLevelChunk`

`ClientChunkCache` stores received chunks.

Important anchors:

- `ClientChunkCache.java:35` client light engine
- `ClientChunkCache.java:41` empty chunk
- `ClientChunkCache.java:59` `drop`
- `ClientChunkCache.java:70` `getChunk`
- `ClientChunkCache.java:100` `replaceWithPacketData`
- `ClientChunkCache.java:127` `updateViewCenter`
- `ClientChunkCache.java:132` `updateViewRadius`
- `ClientChunkCache.java:178` storage class
- `ClientChunkCache.java:196` floor-mod storage index
- `ClientChunkCache.java:270` `inRange`

## Incremental Updates

Block and block-entity updates:

- `ClientPacketListener.java:844` `handleChunkBlocksUpdate`
- `ClientPacketListener.java:930` `handleBlockUpdate`
- `ClientPacketListener.java:1440` `handleBlockEntityData`
- `ClientboundBlockUpdatePacket.java:21` packet `BlockPos`
- `ClientboundSectionBlocksUpdatePacket.java:21` packet `SectionPos`
- `ClientboundSectionBlocksUpdatePacket.java:72` `runUpdates`
- `ClientboundBlockEntityDataPacket.java:26` packet `BlockPos`

Light updates:

- `ClientPacketListener.java:2284` `handleLightUpdatePacket`
- `ClientChunkCache.java:166` `onLightUpdate`
- `ClientboundLightUpdatePacketData.java:27` `ChunkPos`
- `ClientboundLightUpdatePacketData.java:77` `SectionPos.of(...)`

## Audit Questions

- What coordinate space does the client believe a chunk occupies?
- Are full chunks, unloads, block updates, section updates, block entity updates, and light updates all transformed consistently?
- Does the client's floor-mod chunk storage alias unrelated visible chunks when view-center coordinates change?
- Does rendering read from `ClientChunkCache` by visible chunk position only?
- Do block entity renderers rely on packet position, NBT position, or both?

