# Globe World System Plan

Core rule: the canonical tile owns all mutable world state. Raw/virtual chunks outside the tile are views of canonical chunks, not independent worlds.

World period: `W_CHUNKS` chunks, `W_BLOCKS = W_CHUNKS * 16` blocks.
Canonical zone: centered on origin, `[-W/2, W/2)` in chunk/block X/Z.

## System Map

| System | Status | Problem | Chosen solution |
|---|---|---|---|
| Coordinate helpers | Done | Every system needs the same answer for "which canonical tile position is this?" | Keep `CoordUtil` as the single source for wrapping chunks, blocks, positions, wrapped distances, and virtual positions relative to a viewer. |
| Chunk lookup | Done | Vanilla asks for raw chunks, but only canonical chunks should store terrain/block state. | Wrap `ServerChunkCache.getChunk` and `getChunkNow` X/Z into canonical chunk coords before lookup. |
| Chunk packets | Done | Client must render aliases at raw positions while receiving canonical chunk data. | When sending a chunk, load canonical chunk data, then relabel the packet to the raw/alias chunk position on the wire. Forget packets use the raw/alias position. |
| Canonical chunk lifetime | Done | Canonical chunks may unload because players are physically near raw aliases, not near the canonical chunk. | Track non-canonical alias chunk lifecycle with ref-counted, loading-only mod tickets: loaded aliases keep canonical chunks full without mutating vanilla simulation-distance trackers. Clear those tickets on level shutdown. |
| Multiple rendered aliases | Done | Client can render multiple copies of the same canonical chunk; changing one canonical block must update every visible copy. | Track loaded aliases per player and canonical chunk. Fan out block, section, and block-entity update packets to every loaded alias position. |
| Block placement and edits | Done | Edits made through an alias must mutate canonical block state and notify every alias. | Wrap block/chunk access into canonical coords, fan out packets for all visible aliases, and make skipped/reentrant server block-update notifications send the actual post-update state. Verified with water flow and redstone dot-to-line rendering. |
| Block entities | Mostly done | Block entity packets carry absolute positions and would only update one alias. | Treat block entity update packets like block updates: canonical source position, then copy packet once per visible alias with offset position. Audit persistence and ticking separately. |
| Entity storage | Done for mobs | Mobs/entities spawned in aliases can become separate duplicates or live outside canonical space. | Canonicalize mobs before adding them to `ServerLevel`; stored mob X/Z should always be inside the canonical tile. |
| Entity packets | Done | A canonical mob near one tile edge should appear in the nearest virtual copy for each player. | Virtualize outbound add, teleport, and position-sync packets per viewer using `CoordUtil.virtualBlock`. Relative movement packets stay relative where possible. |
| Entity tracking | Done | Vanilla tracking checks raw distance/chunks, so players near a tile edge may not receive nearby canonical entities. | Use wrapped X/Z distance for tracking range and check the virtual chunk nearest to the player. |
| Player provider for block/entity broadcasts | Done | Vanilla asks "which players track canonical chunk X/Z?", but players may track an alias instead. | Convert canonical chunk coords to the player's nearest virtual chunk before `ChunkMap.isChunkTracked` / border checks. |
| Mob natural spawning | Done for chunk pass | Multiple aliases can cause duplicate spawn attempts for the same canonical chunk; raw distance checks break near edges. | Spawn only into canonical chunks/positions, wrap spawn candidate positions, wrap player distance checks, count mob caps by canonical chunk, and dedupe `ChunkMap.collectSpawningChunks` by canonical chunk. |
| Mob chunk-generation spawning | Done | Alias chunk generation could spawn duplicate mobs. | Cancel chunk-generation spawns for non-canonical chunks. |
| Mob despawning | Needs audit | Vanilla nearest-player and despawn distance checks can treat edge-near mobs as far away. | Use wrapped distance for nearest-player selection and despawn distance checks. Confirm current mixins cover `Mob.checkDespawn`; add a targeted mixin if not. |
| Mob sensing and targeting | Partly done | AABB and distance checks do not naturally wrap at tile edges. | Add wrapped player candidates to sensors, use wrapped target distances, and allow line-of-sight fallback when wrapped distance says the target is actually nearby. |
| Mob pathfinding | Open | Path nodes and goals are in raw Euclidean space, so crossing the tile edge can fail or look too far. | MVP: accept imperfect pathing. Later: virtualize path target positions relative to the mob and wrap block reads during node evaluation. |
| Random block ticks | Done for chunk pass | If multiple aliases of one canonical chunk are loaded, crops/fire/ice/etc. may random-tick multiple times per server tick, or alias data may write mutations into canonical storage. | `ChunkMapRandomTickMixin` converts each block-ticking chunk to its canonical chunk and dedupes by canonical chunk key during `ChunkMap.forEachBlockTickingChunk`. |
| Scheduled block/fluid ticks | Done for gameplay path | Scheduled ticks may be queued at raw alias positions or stored on canonical chunks outside vanilla simulation range. | Canonicalize `LevelTicks.schedule`, `hasScheduledTick`, and `willTickThisTick` positions so queued ticks store in canonical block coords. Clone/area tick operations and cross-edge simulation reach still need edge-case testing. |
| Entity ticking | Needs audit | A canonical mob might be ticked once per canonical chunk or accidentally once per alias depending on chunk tick iteration. | Entity storage should make each entity tick once from canonical storage. Verify there is no alias-driven duplicate entity ticking. |
| Redstone and neighbor updates | Partly done | Neighbor positions crossing a tile edge may query/update raw positions; redstone dust shape changes can happen through reentrant immediate block updates. | Reentrant skipped block-update broadcasts now send the actual post-update state, which fixes redstone dot-to-line rendering. Broader cross-edge neighbor notification behavior still needs audit. |
| Terrain noise and biomes | Planned | Terrain and biome generation may not line up visually at canonical tile edges. | Make generation periodic with period `W_BLOCKS`: wrap X/Z inputs for noise, biome lookup, and structure placement seeds. |
| Alias chunk post-processing | Done | Alias `LevelChunk.postProcessGeneration` can apply neighbor-shape fixes from alias terrain into canonical storage through wrapped `Level.setBlock`. | Cancel post-generation processing for non-canonical chunks and clear queued post-processing offsets. |
| Structures | Needs validation | Villages and other structures can cut off at tile boundaries because vanilla separates structure starts, references, piece bounding boxes, and per-chunk placement. | Store virtual source keys during structure-reference generation, resolve them during biome decoration, and place vanilla starts with the exact whole-tile chunk-box shift. Alias starts are still treated as transient worldgen data; persistence and structure-query paths need audit. |
| Dungeons / monster rooms | Open | Monster rooms are features, not structures. Alias feature centers are skipped, so rooms whose origin is outside the canonical tile but whose body crosses into it are missing. | Add a controlled virtual feature-origin pass for edge chunks, or allow selected alias feature decoration to spill only wrapped canonical writes. Audit chest/spawner block entity replay. |
| Players | Planned | Players can move unbounded forever, but saved/log-in positions should stay sane. | Let live player coordinates remain virtual. On world load, respawn, or dimension transfer, rebase to canonical equivalent when appropriate. |
| Client rendering | Partial client visuals | Client sees raw alias chunks as normal chunks; no client-side canonical awareness. | Keep server relabeling. A client terrain shader now bends terrain downward based on a saved curvature percent: 0% disables it, 50% is comfortable, and 100% is realistic. Mutable state still stays canonical. |
| Debug logging | Done | Chunk/client diagnostics are useful while isolating alias send/drop/fanout bugs but too noisy for normal play. | Remove temporary chunk-load instrumentation after the Nether portal loading fix. Keep only targeted warning logs for unrelated mutation audits. |

## High-Risk Questions

1. Mob despawn: is `Mob.checkDespawn` using wrapped distance everywhere it needs to? If not, edge mobs can vanish.
2. Entity ticking: do canonical mobs tick exactly once when only an alias is in entity-ticking range?
3. Cross-edge neighbor updates: do redstone, pistons, observers, doors, and similar blocks notify the correct canonical neighbor when the neighbor lies over a tile boundary?
4. Pathfinding: acceptable for MVP, but likely to be the first visible mob behavior flaw after spawning/despawning are fixed.

## Chunk Tick Plan

Best target model: every server simulation lane either receives canonical chunks only or dedupes by canonical chunk key before running side effects. Alias chunks remain valid view/tracking inputs, but they should not run mutable world behavior directly.

1. Keep canonical ticket mirroring as the availability layer. `CanonicalChunkTickets` should only decide which canonical chunks are loaded because aliases are nearby; simulation lanes should canonicalize/dedupe their own work instead of changing vanilla simulation-distance trackers mid-tick.
2. Treat `ChunkMap.forEachBlockTickingChunk` as the random/block tick lane. Current status: canonicalized and deduped by `ChunkMapRandomTickMixin`.
3. Treat `ChunkMap.collectSpawningChunks` as the spawning/thunder/inhabited-time lane. Current status: canonicalized and deduped by `ChunkMapSpawningMixin`.
4. Audit entity ticking separately. Because mobs are stored canonically, entity ticking should naturally run once from canonical storage, but `isPositionEntityTicking`, despawn distance, and section-change behavior still need proof.
5. After chunk lanes are stable, audit cross-edge neighbor updates and scheduled tick clone/copy edge cases.
