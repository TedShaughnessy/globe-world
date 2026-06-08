# Wandering Trader Alias Spawning Audit

## Status

Investigative. Playtesting has not yet shown a naturally spawned wandering
trader, but this is not confirmed as a Globe World bug. Vanilla trader spawning
is rare, Overworld-only, and controlled by a custom spawner outside the ordinary
`NaturalSpawner` path.

## Prompt

During playtesting, no wandering trader has appeared yet. Before adding a fix,
confirm whether the absence is normal vanilla rarity, test setup, or an
alias-coordinate spawn failure.

## Vanilla Mechanics

Checked against Minecraft 26.1.2 Loom common sources.

`MinecraftServer.createLevels(...)` installs these custom spawners only for the
Overworld:

- `PhantomSpawner`
- `PatrolSpawner`
- `CatSpawner`
- `VillageSiege`
- `WanderingTraderSpawner`

`WanderingTraderSpawner.tick(...)`:

- Requires `GameRules.SPAWN_WANDERING_TRADERS`.
- Runs after an internal 1200-tick delay.
- Decrements saved `WanderingTraderData.spawnDelay()` by 1200.
- When delay reaches zero, resets it to 24000, increases saved spawn chance up
  to 75, and rolls that saved chance.
- Even after the saved chance roll passes, `spawn(...)` has an extra
  `random.nextInt(10) != 0` gate before searching for a position.

`WanderingTraderSpawner.spawn(...)`:

- Chooses `level.getRandomPlayer()`.
- Uses `player.blockPosition()` as the base position.
- Searches for a meeting POI within 48 blocks of that raw player position.
- Uses the meeting POI, or the raw player position if no POI is found, as the
  reference position.
- Calls `findSpawnPositionNear(level, referencePos, 48)`.

`findSpawnPositionNear(...)`:

- Makes up to 10 random X/Z attempts around the reference position.
- Computes Y with `level.getHeight(...)`.
- Accepts the first candidate whose wandering-trader spawn placement type says
  the position is valid.

## Current Globe Behavior

Covered paths:

- `NaturalSpawnerMixin` wraps ordinary natural-spawn candidate positions,
  player-distance checks, counted mob chunks, and chunk-generation mob spawns.
- `ChunkMapSpawningMixin` dedupes ordinary spawning chunks by canonical chunk
  key before `ServerChunkCache.tickSpawningChunk(...)`.
- `ServerChunkCacheMixin` wraps `getChunk(...)` and `getChunkNow(...)` to return
  canonical chunks for alias chunk requests.
- Non-player entities, including wandering traders and trader llamas after they
  spawn, are canonicalized by the ordinary entity storage/tick hooks.

Known gaps or suspicions:

- Wandering traders use `WanderingTraderSpawner`, not `NaturalSpawner`, so the
  ordinary natural-spawn mixins do not cover their player/reference/candidate
  coordinates.
- A player standing in a raw alias tile may give the trader spawner an alias
  `player.blockPosition()`.
- Trader candidate X/Z positions around that alias player can lie outside the
  canonical tile.
- Vanilla `Level.getHeight(...)` first asks `hasChunk(rawChunkX, rawChunkZ)`
  before it calls `getChunk(...)`. Globe currently wraps `getChunk(...)`, but
  does not have a matching `ServerChunkCache.hasChunk(...)` wrapper.
- If `hasChunk(...)` rejects an alias chunk that would wrap to a loaded
  canonical chunk, `getHeight(...)` can return the level minimum Y and make the
  spawn placement attempt fail.

These are plausible failure modes, not confirmed evidence.

## Confirmation Plan

Add temporary diagnostics around `WanderingTraderSpawner.spawn(...)` and
`findSpawnPositionNear(...)`. Keep this pass logging-only.

Log one line per spawn attempt with:

- Dimension and whether `SPAWN_WANDERING_TRADERS` is enabled.
- Current saved trader `spawnDelay` and `spawnChance`, if accessible at the log
  site.
- Selected player raw block position and canonical block position.
- Whether the selected player is in the canonical tile.
- Meeting POI result, if present, and its canonical position.
- Reference position used for candidate generation.

Log one line per candidate with:

- Raw candidate X/Z and canonical candidate X/Z.
- Raw candidate chunk and canonical candidate chunk.
- Raw `hasChunk(...)` result and wrapped/canonical `hasChunk(...)` result.
- `level.getHeight(...)` result.
- Final candidate `BlockPos`.
- Spawn-placement result.
- `hasEnoughSpace(...)` result, if reachable without changing behavior.
- Biome exclusion result for `BiomeTags.WITHOUT_WANDERING_TRADER_SPAWNS`.

Test cases:

- Overworld, player in canonical tile, no nearby meeting POI.
- Overworld, player in a raw X alias tile via `/globeworld teleport_alias`.
- Overworld, player in a raw Z alias tile via `/globeworld teleport_alias`.
- Overworld, player near a bell/meeting POI in canonical coordinates.
- Overworld, player near a visible alias of a meeting POI.

Useful controls:

- Confirm `/gamerule spawn_wandering_traders true`.
- Use a long enough test window or temporarily force the saved delay/chance
  only for diagnostics.
- Compare with an untiled or canonical-tile session before concluding the alias
  path is broken.

## Confirmation Criteria

Treat this as confirmed if alias-tile attempts fail for coordinate reasons that
canonical or wrapped candidates would avoid. Strong signals include:

- Raw `hasChunk(...)` is false while wrapped/canonical `hasChunk(...)` is true.
- Candidate Y becomes the level minimum only for alias-frame candidates.
- Spawn placement fails at alias raw positions but would pass at the wrapped
  canonical equivalent.
- Meeting POI search misses a wrapped-near visible alias that should guide the
  trader spawn.

Do not treat this as confirmed if attempts only fail because of ordinary vanilla
random rolls, biome exclusions, lack of space, or normal low spawn frequency.

## Possible Fix Shapes

Choose only after confirmation:

- Add a `WanderingTraderSpawnerMixin` that canonicalizes the selected player
  position, POI reference position, and random spawn candidates before height,
  placement, and biome checks.
- Wrap `ServerChunkCache.hasChunk(...)` for alias chunk requests if diagnostics
  show `Level.getHeight(...)` is the general failure point and the broader
  behavior is safe for other systems.
- Supplement POI lookup so a player standing at a visible alias of a meeting POI
  can still use the canonical POI as the trader reference.

## Non-Goals

- Do not make wandering traders more common than vanilla.
- Do not spawn traders outside the Overworld unless that becomes an explicit
  feature.
- Do not duplicate trader or llama entities per alias.
- Do not change trader inventory, despawn delay, leashing, or village schedule
  behavior.

## Related Mechanics

- [Entities](../mod-mechanics/entities.md)
- [Chunks](../mod-mechanics/chunks.md)
- [Vanilla mobs and entities](../vanilla-mechanics/mobs-and-entities.md)
