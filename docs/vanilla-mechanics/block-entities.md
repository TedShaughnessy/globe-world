# Block Entities

Block entities are stored by `BlockPos` inside `LevelChunk`, ticked through level-level ticker lists, persisted in chunk data, and synchronized with explicit packets or full chunk data.

## Key Source Files

Common sources jar:

- `net/minecraft/world/level/chunk/LevelChunk.java`
- `net/minecraft/world/level/block/entity/BlockEntity.java`
- `net/minecraft/world/level/block/entity/BlockEntityType.java`
- `net/minecraft/world/level/Level.java`
- `net/minecraft/server/level/ServerLevel.java`
- `net/minecraft/network/protocol/game/ClientboundBlockEntityDataPacket.java`
- `net/minecraft/network/protocol/game/ClientboundLevelChunkPacketData.java`

Client-only sources jar:

- `net/minecraft/client/multiplayer/ClientPacketListener.java`

## Storage And Lifecycle

`LevelChunk` owns the block entity maps and ticking wrappers.

Important anchors:

- `LevelChunk.java:91` `tickersInLevel`
- `LevelChunk.java:149` block entities copied from proto chunk
- `LevelChunk.java:279` `setBlockState`
- `LevelChunk.java:316` remove old block entity after block state change
- `LevelChunk.java:338` create block entity for new state
- `LevelChunk.java:377` `getBlockEntity`
- `LevelChunk.java:409` `addAndRegisterBlockEntity`
- `LevelChunk.java:436` `setBlockEntity`
- `LevelChunk.java:467` `getBlockEntityNbtForSaving`
- `LevelChunk.java:485` `removeBlockEntity`
- `LevelChunk.java:614` `promotePendingBlockEntity`
- `LevelChunk.java:699` `registerAllBlockEntitiesAfterLevelLoad`
- `LevelChunk.java:720` `updateBlockEntityTicker`

`Level` provides higher-level access and ticking.

Important anchors:

- `Level.java:111` `blockEntityTickers`
- `Level.java:529` `addBlockEntityTicker`
- `Level.java:532` `tickBlockEntities`
- `Level.java:546` `shouldTickBlocksAt(ticker.getPos())`
- `Level.java:703` `getBlockEntity`
- `Level.java:707` `setBlockEntity`
- `Level.java:879` `blockEntityChanged`

`ServerLevel` integrates block entities with chunk unload and debug sync:

- `ServerLevel.java:441` `tickBlockEntities`
- `ServerLevel.java:1001` `unload`
- `ServerLevel.java:1962` `onBlockEntityAdded`

## Save, Load, And Update Packets

`BlockEntity` stores an immutable world position and exposes update packet/tag hooks.

Important anchors:

- `BlockEntity.java:51` `worldPosition`
- `BlockEntity.java:73` `getPosFromTag`
- `BlockEntity.java:184` `loadStatic`
- `BlockEntity.java:213` `setChanged`
- `BlockEntity.java:226` `getBlockPos`
- `BlockEntity.java:235` `getUpdatePacket`
- `BlockEntity.java:239` `getUpdateTag`

`ClientboundBlockEntityDataPacket` carries block entity position, type, and tag:

- `ClientboundBlockEntityDataPacket.java:26` `pos`
- `ClientboundBlockEntityDataPacket.java:27` `type`
- `ClientboundBlockEntityDataPacket.java:28` `tag`
- `ClientboundBlockEntityDataPacket.java:30` `create`
- `ClientboundBlockEntityDataPacket.java:54` `getPos`

Client application:

- `ClientPacketListener.java:1440` `handleBlockEntityData`
- `ClientPacketListener.java:849` full chunk with block entity tags

## Audit Questions

- Is a block entity unique by canonical storage position, visible position, or both?
- When a block entity packet is sent, should `BlockPos` be transformed independently from its NBT?
- Does the block entity save tag contain coordinates that must agree with chunk-local storage?
- Are tickers added and removed exactly once per actual storage block entity?
- Do comparator, inventory, sign, container-open, and game-event side effects use the same position identity?

