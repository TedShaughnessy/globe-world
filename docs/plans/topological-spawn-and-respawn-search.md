# Topological Spawn And Respawn Search Plan

This plan resolves spawn-position search gaps raised in the
[Minecraft coordinate coverage audit](minecraft-coordinate-coverage-audit.md).
It covers vanilla code that chooses, validates, or mutates spawn/respawn
positions using raw X/Z coordinates before Globe World's lower-level
canonicalization can take over.

## Problem

Globe World already canonicalizes stored non-player entities, player lifecycle
positions, natural-spawn candidate chunks, and many spawn-adjacent POI queries.
Several vanilla spawn searches still perform their own raw coordinate probing:

- `PlayerSpawnFinder` loads and checks raw candidate chunks for world/player
  spawn fallback.
- `ServerPlayer.findRespawnAndUseSpawnBlock(...)` reads and mutates bed,
  respawn-anchor, and forced respawn positions from raw saved metadata.
- `NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint(...)` compares spawn
  candidates to world spawn with raw Euclidean distance.
- `WanderingTraderSpawner.findSpawnPositionNear(...)`,
  `VillageSiege.findRandomSpawnPos(...)`, and `SpawnUtil.trySpawnMob(...)`
  search for event/custom spawn positions around a reference point.

Some of these are merely presentation or duplicate-entity risks that downstream
entity canonicalization softens. Others can load alias chunks, reject valid
visible spawn locations, or mutate the wrong raw respawn-anchor coordinate.

## Goals

- Keep canonical chunks and canonical block positions as the durable owners.
- Make player/world spawn lookup operate in canonical storage while preserving
  vanilla respawn radius, height, collision, and fallback behavior.
- Make bed, respawn-anchor, and forced respawn metadata resolve through the
  canonical block owner before validation or mutation.
- Apply wrapped distance to the natural-spawn world-spawn exclusion.
- Fix custom/event spawn searches whose gameplay meaning is visibly
  topological, especially wandering traders and village sieges.
- Document any remaining raw/admin spawn metadata policy explicitly.

## Non-Goals

- Persisting alias respawn metadata.
- Making commands report alias-local coordinates unless a command policy later
  asks for it.
- Replacing every generic entity spawn helper globally without caller review.
- Changing vanilla spawn rules, group sizes, cooldowns, biome filters, or mob
  cap semantics except where raw coordinate space leaks through.

## Vanilla Source Anchors

- `net/minecraft/server/level/PlayerSpawnFinder.java`
  - `findSpawn(...)`
  - `scheduleCandidate(...)`
  - `getOverworldRespawnPos(...)`
  - `getSpawnPosInChunk(...)`
- `net/minecraft/server/level/ServerPlayer.java`
  - `adjustSpawnLocation(...)`
  - `findRespawnPositionAndUseSpawnBlock(...)`
  - `findRespawnAndUseSpawnBlock(...)`
- `net/minecraft/server/players/PlayerList.java`
  - `respawn(...)`
- `net/minecraft/world/level/NaturalSpawner.java`
  - `isRightDistanceToPlayerAndSpawnPoint(...)`
- `net/minecraft/world/entity/npc/wanderingtrader/WanderingTraderSpawner.java`
  - `spawn(...)`
  - `tryToSpawnLlamaFor(...)`
  - `findSpawnPositionNear(...)`
- `net/minecraft/world/entity/ai/village/VillageSiege.java`
  - `tryToSetupSiege(...)`
  - `trySpawn(...)`
  - `findRandomSpawnPos(...)`
- `net/minecraft/util/SpawnUtil.java`
  - `trySpawnMob(...)`

## Current Globe Anchors

- `CoordUtil`
- `DimensionTiling`
- `TopologyContext`
- `TopologyContexts`
- `PlayerCanonicalizer`
- `EntityCanonicalizer`
- `ServerChunkCacheMixin`
- `LevelSetBlockBroadcastMixin`
- `PlayerListCanonicalPositionMixin`
- `ServerPlayerCanonicalPositionMixin`
- `NaturalSpawnerMixin`
- `ChunkMapSpawningMixin`
- `TopologicalPoiQueries`
- `WanderingTraderSpawnerPoiMixin`
- `CatSpawnerPoiMixin`
- `PatrolSpawnerLocalDaylightMixin`

## Implementation Phases

### 1. PlayerSpawnFinder And Default Spawn

Add a focused mixin for `PlayerSpawnFinder`.

Required behavior:

- Canonicalize the `spawnSuggestion` X/Z before candidate search when tiling is
  enabled for the level.
- Canonicalize candidate X/Z before creating `SPAWN_SEARCH` tickets so alias
  chunks are not loaded as durable search owners.
- Run heightmap, block-state, fluid, and collision checks against the canonical
  candidate position.
- Return a canonical spawn position to the server. Existing player lifecycle
  packet paths can keep sending canonical local-player coordinates.
- Keep world-border radius behavior conservative: use vanilla raw border
  limiting unless a finite-world border policy explicitly changes it.

Potential hook points:

- wrap `PlayerSpawnFinder.findSpawn(...)` to pass a canonical
  `spawnSuggestion`;
- wrap `new ChunkPos(chunkX, chunkZ)` or `addTicketAndLoadWithRadius(...)` in
  `scheduleCandidate(...)` to use canonical chunk coordinates;
- modify X/Z arguments in `getOverworldRespawnPos(...)` and
  `getSpawnPosInChunk(...)` to canonical block/chunk coordinates.

Risk mitigation:

- Avoid changing Y search semantics.
- Avoid introducing alias-space return positions; player lifecycle hooks already
  canonicalize login and respawn.
- Add manual tests for first login, missing bed respawn fallback, and initial
  world spawn near both X and Z seams.

### 2. Bed, Anchor, And Forced Respawn Blocks

Add a `ServerPlayer` respawn-block mixin or helper that canonicalizes
`RespawnConfig.respawnData().pos()` before vanilla validates or mutates the
block.

Required behavior:

- Bed and respawn-anchor lookup must read the canonical owner block.
- Respawn-anchor charge decrement must write the canonical owner block.
- Forced respawn free-space checks must test the canonical owner column.
- Returned stand-up position should be canonical unless a later packet layer
  deliberately virtualizes it for a specific viewer.
- Saved command metadata may remain raw; canonicalization happens at use time.

Potential hook points:

- wrap `LevelData.RespawnData.pos()` or the local `BlockPos pos` in
  `findRespawnAndUseSpawnBlock(...)`;
- or wrap the full static helper and call vanilla with a canonicalized
  `RespawnConfig`.

Risk mitigation:

- Preserve the original yaw/pitch and forced flag.
- Do not write back canonical metadata to the player's saved spawn point unless
  a separate command/lifecycle policy chooses that.
- Ensure the respawn-anchor depletion sound uses the same canonical owner that
  was mutated, then rely on packet virtualization for the visible sound
  position.

### 3. Natural-Spawn World-Spawn Exclusion

Extend `NaturalSpawnerMixin` so the world-spawn proximity guard uses wrapped
distance.

Required behavior:

- Preserve the vanilla 24-block exclusion radius.
- Compare the natural-spawn candidate position to `level.getRespawnData().pos()`
  with wrapped X/Z distance when the respawn data belongs to the same
  dimension.
- Keep the existing wrapped nearest-player distance and canonical chunk cap
  behavior.

Potential hook point:

- wrap `BlockPos.closerToCenterThan(...)` inside
  `isRightDistanceToPlayerAndSpawnPoint(...)`, or replace the loaded
  `respawnData.pos()` with a candidate-local alias before vanilla compares it.

Risk mitigation:

- Do not alter spawn cap accounting.
- Test candidates just across an X seam and a Z seam from world spawn.

### 4. Wandering Trader Spawn Search

Extend the current trader POI fix beyond meeting-point lookup.

Required behavior:

- `findSpawnPositionNear(...)` should sample around the visible reference point
  but validate and return canonical owner positions.
- Trader llama spawn search should use the already-spawned trader's canonical
  position as storage owner while preserving the visible local radius.
- `setWanderTarget(...)` and `setHomeTo(...)` should remain canonical memory
  targets, matching existing POI-memory policy.

Potential hook points:

- modify sampled `xPosition` and `zPosition` before `level.getHeight(...)`;
- wrap `new BlockPos(xPosition, yPosition, zPosition)` to canonicalize X/Z;
- or replace `findSpawnPositionNear(...)` with a small helper that evaluates
  candidate aliases and returns the canonical candidate.

Risk mitigation:

- Keep vanilla attempt count, radius, biome filter, spacing check, and entity
  spawn reason.
- Confirm the existing `WanderingTraderSpawnerPoiMixin` remains responsible
  only for meeting-point discovery.

### 5. Village Siege Spawn Search

Add a village-siege mixin after trader/player paths are stable.

Required behavior:

- `tryToSetupSiege(...)` should treat villages across seams as nearby when
  choosing a player village center.
- `findRandomSpawnPos(...)` should wrap height, village, and monster spawn-rule
  checks to the canonical owner position.
- Spawned zombies remain canonical non-player entities through existing entity
  storage hooks.

Potential hook points:

- use `TopologicalPoiQueries.sectionsToVillage(...)` coverage through
  `ServerLevel.isVillage(...)` where possible;
- canonicalize sampled X/Z before height and spawn-rule checks;
- optionally choose candidate aliases around the player-visible siege ring for
  tiny tiles.

Risk mitigation:

- Preserve vanilla siege timing, night gate, biome exclusion, angle/radius, and
  zombie count.
- Treat local sky/time changes as a separate concern unless testing finds a
  daylight gate mismatch.

### 6. SpawnUtil Caller Audit

Do not replace `SpawnUtil.trySpawnMob(...)` globally until each caller is
classified.

Known callers:

- `Villager.spawnGolemIfNeeded(...)`
- `CreakingHeartBlockEntity.spawnProtector(...)`
- `SculkShriekerBlockEntity.trySummonWarden(...)`

Classification questions:

- Can the `start` position be a raw alias, or is it already canonical storage?
- Does the caller's gameplay meaning need visible topological range?
- Does `worldBorder.isWithinBounds(...)` intentionally remain raw?
- Would canonicalizing `searchPos` before `EntityType.create(...)` change
  vanilla caller semantics beyond storage ownership?

Default action:

- Document caller-specific decisions in
  `docs/mod-mechanics/entity-query-caller-matrix.md` or
  `docs/mod-mechanics/entities.md`.
- Add targeted mixins for callers that can start from alias-visible gameplay.
- Leave purely canonical block-entity callers alone if their block position is
  already canonical.

## Suggested Order

1. `PlayerSpawnFinder` and default spawn.
2. `ServerPlayer.findRespawnAndUseSpawnBlock(...)`.
3. Natural-spawn world-spawn exclusion.
4. Wandering trader spawn-position search.
5. Village siege spawn-position search.
6. `SpawnUtil` caller audit and any targeted follow-up hooks.

## Regression Ideas

- Set world spawn within 24 blocks of each tile edge, then verify natural mobs
  do not spawn through the seam inside the protected radius.
- Set a bed and respawn anchor at a canonical edge, store an alias spawn point,
  die, and verify respawn lookup, anchor charge decrement, sound, and final
  player position.
- Delete or obstruct a bed/anchor near a seam and verify fallback world spawn
  search loads canonical chunks only.
- Start a new world with tiny tile settings and verify initial spawn search
  does not create durable alias chunks.
- Put a meeting POI across a seam from a player and verify wandering trader
  and llama placement succeeds near the visible village without duplicate
  entity storage.
- Trigger a village siege with the village center visible through a seam and
  verify setup and zombie spawns use canonical entity storage.
- Trigger villager golem, creaking heart, and shrieker/warden spawns near seams
  and classify whether vanilla behavior is acceptable or needs targeted hooks.

## Documentation Exit Criteria

When implemented, move durable behavior into:

- [Entities](../mod-mechanics/entities.md) for player spawn lifecycle, natural
  spawning, event/custom spawners, and entity storage effects;
- [Commands And Admin Coordinates](../mod-mechanics/commands.md) if command
  spawn metadata policy changes;
- [POI And Villages](../mod-mechanics/poi-and-villages.md) for trader and
  village-siege POI/village interactions.

Then replace this plan with a short historical note or remove it from the plan
index.
