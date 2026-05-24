# Mobs And Entities

Entities use continuous world coordinates, chunk/section indexing for storage and ticking, player distance checks for spawning/tracking, and packet synchronization for client visibility.

## Key Source Files

Common sources jar:

- `net/minecraft/server/level/ServerLevel.java`
- `net/minecraft/server/level/ServerChunkCache.java`
- `net/minecraft/server/level/ChunkMap.java`
- `net/minecraft/server/level/ServerEntity.java`
- `net/minecraft/world/entity/Entity.java`
- `net/minecraft/world/entity/Mob.java`
- `net/minecraft/world/level/NaturalSpawner.java`
- `net/minecraft/world/level/LocalMobCapCalculator.java`
- `net/minecraft/world/level/entity/PersistentEntitySectionManager.java`
- `net/minecraft/world/level/entity/EntitySectionStorage.java`
- `net/minecraft/world/level/entity/EntityTickList.java`

## Entity Ticking

`ServerLevel` owns the entity manager and tick list.

Important anchors:

- `ServerLevel.java:202` `entityTickList`
- `ServerLevel.java:205` `entityManager`
- `ServerLevel.java:417` entity tick loop
- `ServerLevel.java:423` entity-ticking range check
- `ServerLevel.java:812` `tickNonPassenger`
- `ServerLevel.java:826` `tickPassenger`
- `ServerLevel.java:948` `addFreshEntity`
- `ServerLevel.java:983` `addEntity`
- `ServerLevel.java:1830` `getEntities`
- `ServerLevel.java:1886` `areEntitiesLoaded`
- `ServerLevel.java:1894` `isPositionEntityTicking`

Entity manager callbacks wire entities into chunk tracking:

- `ServerLevel.java:1993` `onCreated`
- `ServerLevel.java:2007` `onTickingStart`
- `ServerLevel.java:2015` `onTrackingStart`
- `ServerLevel.java:2050` `onTrackingEnd`
- `ServerLevel.java:2079` `onSectionChange`

## Natural Spawning

`ServerChunkCache.tickChunks(...)` computes spawn state and calls natural spawning for eligible chunks.

Important anchors:

- `ServerChunkCache.java:372` spawn state creation
- `ServerChunkCache.java:393` collect spawning chunks
- `ServerChunkCache.java:399` tick spawning chunk
- `ServerChunkCache.java:415` `tickSpawningChunk`

`NaturalSpawner` handles cap checks, player distance, biome/structure spawn lists, and per-position tests.

Important anchors:

- `NaturalSpawner.java:69` `createState`
- `NaturalSpawner.java:106` `getFilteredSpawningCategories`
- `NaturalSpawner.java:123` `spawnForChunk`
- `NaturalSpawner.java:156` `spawnCategoryForPosition`
- `NaturalSpawner.java:187` nearest player lookup
- `NaturalSpawner.java:231` `isRightDistanceToPlayerAndSpawnPoint`
- `NaturalSpawner.java:257` despawn-distance guard
- `NaturalSpawner.java:287` `isValidPositionForMob`
- `NaturalSpawner.java:343` `getRandomPosWithin`
- `NaturalSpawner.java:366` chunk-generation creature spawning
- `NaturalSpawner.java:532` global cap check
- `NaturalSpawner.java:537` local cap check

## Player Tracking And Entity Packets

`ChunkMap` owns player chunk tracking and entity tracking.

Important anchors:

- `ChunkMap.java:145` `playerMap`
- `ChunkMap.java:146` `entityMap`
- `ChunkMap.java:929` collect spawn candidate chunks
- `ChunkMap.java:954` `anyPlayerCloseEnoughForSpawning`
- `ChunkMap.java:998` `playerIsCloseEnoughForSpawning`
- `ChunkMap.java:1028` `updatePlayerStatus`
- `ChunkMap.java:1056` `move`
- `ChunkMap.java:1092` `updateChunkTracking`
- `ChunkMap.java:1128` `addEntity`
- `ChunkMap.java:1154` `removeEntity`
- `ChunkMap.java:1170` entity tracking tick
- `ChunkMap.java:1203` `sendToTrackingPlayers`
- `ChunkMap.java:1219` `sendToTrackingPlayersAndSelf`
- `ChunkMap.java:1378` `TrackedEntity.updatePlayer`
- `ChunkMap.java:1380` player/entity delta
- `ChunkMap.java:1383` horizontal distance squared

## Audit Questions

- Does an entity's true position differ from the position a player should receive?
- Which distance checks should use wrapped/topological distance rather than Euclidean world distance?
- Are entity section storage keys in storage coordinates or visible coordinates?
- Are spawn caps counted once per physical chunk or once per visible chunk?
- Do pathfinding, sensors, and targeting use the same distance convention as tracking?

