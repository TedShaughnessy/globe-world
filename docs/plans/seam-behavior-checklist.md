# Seam-Behavior Checklist

This checklist defines seam behavior Globe World should preserve during future
feature work and refactors. It is written as a manual test matrix first, with
notes for cases that could later become automated game tests or diagnostics.

Use small custom tiles for stress testing. A 4x4 chunk Overworld tile exposes
chunk, block, entity, and packet seams quickly; repeat high-risk checks in the
Nether when dimension scaling or Nether-specific generation matters. The End
should remain vanilla/untiled.

Status keys:

- `Manual`: practical playtest or command-driven check.
- `Candidate automated`: suitable for a focused future test or diagnostic.
- `Open design`: depends on an unimplemented primitive or feature plan.

## Core Topology

| Case | Setup | Expected behavior | Status |
| --- | --- | --- | --- |
| Canonical block ownership | Place and break blocks on both sides of an X seam and a Z seam. | The canonical tile stores one durable block state; aliases show the same result. | Manual |
| Canonical chunk lookup | Stand outside the canonical tile and force chunk access through block placement, block entities, or commands. | Server lookups resolve to the canonical owner without creating durable alias chunks. | Candidate automated |
| Corner wrapping | Repeat block and chunk checks at an X/Z corner alias. | Both axes wrap together and the visible corner behaves like the canonical corner. | Manual |
| Disabled dimensions | Repeat a simple seam check in the End. | The End behaves like vanilla space, with no topology wrapping. | Manual |
| Overworld/Nether split | Use different Overworld and Nether tile sizes. | Each dimension uses its own topology settings and no shared global tile math leaks across dimensions. | Manual |

## Blocks, Ticks, And Neighbor Behavior

| Case | Setup | Expected behavior | Status |
| --- | --- | --- | --- |
| Neighbor updates across a seam | Put redstone, observers, doors, or pistons on opposite visible sides of a seam. | Updates use canonical ownership while visible aliases respond as if adjacent. | Manual |
| Scheduled ticks near a seam | Test crops, fluids, redstone delays, or block updates at the tile edge. | Scheduled tick storage and lookup canonicalize consistently. | Candidate automated |
| Alias mutation guard | Try a client action against an alias whose canonical chunk is not block-ticking. | The server rejects unsafe alias mutation and diagnostics can report the decision when enabled. | Candidate automated |
| Block entities across a seam | Place chests, signs, furnaces, or similar block entities on alias edges. | Block-entity access resolves to canonical storage and packets render at the visible alias. | Manual |
| Light and section updates | Change blocks that affect light near a seam. | Loaded aliases receive section/light updates without persistent alias state. | Manual |

## Packet Virtualization

| Case | Setup | Expected behavior | Status |
| --- | --- | --- | --- |
| Full chunk packet alias | Join or move so an alias chunk loads. | The client receives canonical chunk data labeled as the visible alias chunk. | Candidate automated |
| Forget chunk packet | Move away until an alias chunk unloads. | The loaded-alias tracker forgets the visible alias without touching canonical state. | Candidate automated |
| Block update fanout | Place, break, or update a block visible through multiple loaded aliases. | Every loaded alias receives the visible update; unloaded aliases are not spammed. | Manual |
| World events | Trigger sounds, particles, block events, break progress, and explosions near a seam. | Receivers get the nearest or loaded alias according to the packet policy table. | Manual |
| Navigation UI | Use maps, compass/lodestone behavior, and waypoints near a seam. | UI packets use receiver-nearest aliases and wrapped range/visibility checks. | Manual |

## Entities, AI, And Combat

| Case | Setup | Expected behavior | Status |
| --- | --- | --- | --- |
| Entity tracking through seam | Put a mob near one edge and watch from the opposite visible edge. | The mob renders at the nearest visible alias while canonical entity identity remains singular. | Manual |
| Entity target selection | Put a mob and target on opposite visible sides of a seam. | Nearest-target decisions use wrapped distance when an actor context exists. | Candidate automated |
| Look and line of sight | Use mobs that track a player across a seam. | Look control and line-of-sight checks use the actor-local target frame. | Manual |
| Melee reach and knockback | Fight a mob or player across a seam. | Reach checks, hit position, and knockback direction match the visible alias relation. | Manual |
| Path target candidates | Put a target just across a seam with a reachable route. | Pathing can choose useful alias target blocks without claiming full toroidal pathfinding. | Manual |
| Mounted stacks | Move non-player mounts/passengers across a seam. | The mounted stack remains canonical and shifts together. | Manual |
| Player interaction reach | Interact with entities or pickups visible across a seam. | Reach and pickup checks use wrapped target boxes while packets keep canonical identity. | Manual |

## Raycasts And Projectiles

| Case | Setup | Expected behavior | Status |
| --- | --- | --- | --- |
| Block picking | Pick blocks across the visible seam on the client. | The hit result corresponds to the visible alias and canonical block owner. | Manual |
| Arrow-family block hit | Shoot an arrow or trident at a block just across a seam. | Server block collision uses the topological clip and hits the visible target. | Implemented; manual regression |
| Arrow-family entity hit | Shoot an entity visible across a seam. | Entity collision tests visible alias hitboxes and reports the canonical entity. | Implemented; manual regression |
| Thrown projectile move-vector path | Throw snowballs, eggs, ender pearls, potions, or similar projectiles across a seam. | The shared server move-vector path can hit wrapped blocks/entities. | Implemented; manual regression |
| Shared view/attack ray helpers | Brush a block or use an attack-range component weapon across a seam. | Server validation and entity sweeps use wrapped block/entity targets. | Implemented; manual regression |
| Class-specific projectile visuals | Check fireworks, shulker bullets, fishing bobbers, wind charges, fireballs, and trident return. | Server authority remains correct; any client-side correction snaps or visual discontinuities are recorded. | Visual polish |
| Long ray behavior | Test long lines of sight or projectiles spanning more than one tile width. | Alias scan radius limits are understood and documented for that caller. | Regression watch |
| Raycast diagnostic command | Add or use a future `/globeworld raycast` command at a seam. | The report shows raw, canonical, visible hit, entity, and block frames. | Optional diagnostic |

## Portals, Travel, And Player State

| Case | Setup | Expected behavior | Status |
| --- | --- | --- | --- |
| Player seam crossing | Walk, sprint, fly, and teleport across X, Z, and corner seams. | Player position stays playable in raw space while canonical interactions remain wrapped. | Manual |
| Portal scaling | Travel between Overworld and Nether near tile edges. | Target positions use configured dimension scale and then wrap into the target topology. | Manual |
| Respawn or command teleport near seam | Set spawn or teleport around an edge. | Player-facing coordinates remain usable and server state does not duplicate canonical chunks. | Manual |

## Worldgen And Structures

| Case | Setup | Expected behavior | Status |
| --- | --- | --- | --- |
| Terrain edge continuity | Generate new chunks around all tile edges. | Terrain and biome sampling wrap without obvious terrain discontinuity beyond known mode limits. | Manual |
| Feature spillover | Generate features that write across an edge, such as trees or decorations. | Wrapped writes land in the canonical target once observable and do not replay forever. | Manual |
| Structure edge behavior | Generate structures whose pieces cross a tile edge. | Canonical structure ownership is preserved; virtual edge pieces do not become independent owners. | Manual |
| Nether fortress progression | Generate Nether fortresses near edges. | Progression-critical pieces remain available under the current structure edge policy. | Manual |
| `GenerationWindow` replacement | Replace spillover behavior with an explicit toroidal generation window. | Reads, writes, deferred writes, and unsafe destinations have named policies. | [Worldgen improvements](worldgen-improvements.md) |

## Diagnostics And Regression Notes

When a check fails, record:

- dimension and tile settings;
- raw player/entity/block coordinates;
- canonical block/chunk coordinates;
- visible alias coordinates;
- loaded aliases for the relevant canonical chunk;
- enabled diagnostics channel output, if any.

Useful channels while testing include `chunks`, `packets`, `entities`,
`block_mutation`, `worldgen`, `client_cache`, and `portals`.

## Automation Candidates

The best first automated checks are the ones with crisp state assertions:

1. canonical block/chunk ownership at X, Z, and corner seams;
2. scheduled tick canonicalization at an edge;
3. loaded-alias tracking for chunk load/forget;
4. actor-local distance and target view calculations;
5. topological clip identity for a block across one seam.
