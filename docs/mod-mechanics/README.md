# Globe World Mod Mechanics

This folder is the progressive-disclosure index for Globe World's own mechanics.
It documents what the mod changes, why that change exists, and which project
files implement it.

Use [../vanilla-mechanics/README.md](../vanilla-mechanics/README.md) first when
you need vanilla source anchors. Use this folder when you need the mod-side
topology model and the mixins/utilities that enforce it.

## Core Invariant

The canonical tile owns all mutable world state. Coordinates outside the tile are
virtual aliases. Server-side reads and writes canonicalize X/Z before touching
state, while outbound packets are relabeled so clients can render aliases at the
ordinary world coordinates they are tracking.

World period:

- `W_CHUNKS`: configured tile width in chunks.
- `W_BLOCKS = W_CHUNKS * 16`.
- Canonical zone: centered on origin, `[-W/2, W/2)` in chunk/block X/Z.

## Start Here

1. [Topology](topology.md): coordinate wrapping, virtual coordinates, and
   dimension-specific tiling.
2. [Chunks](chunks.md): canonical chunk lookup, alias tickets, chunk
   packet relabeling, and visible alias tracking.
3. [Blocks And Ticks](blocks-and-ticks.md): block mutation, update
   fanout, block entities, random ticks, and scheduled ticks.
4. [Entities](entities.md): canonical entity storage, virtualized entity
   packets, tracking, spawning, despawning, sensing, and pathfinding risks.
5. [Worldgen](worldgen.md): periodic terrain modes, feature spillover,
   structure edge handling, and generation risks.
6. [Client](client.md): client-facing packet/render behavior, curvature,
   diagnostics, and planned client cache work.
7. [Maps](maps.md): filled-map pixel updates and player marker aliasing.
8. [Scrolling Day/Night](scrolling-day-night.md): local day/night presentation,
   saved day-length multiplier, and gameplay across the canonical tile.
9. [Local Solar Time](local-solar-time.md): shared longitude-based local time
   math for scrolling day/night rendering and gameplay hooks.

## Status By Area

| Area | Status | Mechanic file |
| --- | --- | --- |
| Coordinate helpers | Done | [topology.md](topology.md) |
| Dimension-specific tiling | Implemented, needs validation | [topology.md](topology.md) |
| Chunk lookup and packet relabeling | Done | [chunks.md](chunks.md) |
| Canonical chunk lifetime | Done | [chunks.md](chunks.md) |
| Multiple rendered aliases | Done | [chunks.md](chunks.md) |
| Block edits and block/light packets | Done, needs light seam validation | [blocks-and-ticks.md](blocks-and-ticks.md) |
| Position-bearing packet audit | Done for Minecraft 26.1.2 | [client.md](client.md) |
| Block entities | Mostly done | [blocks-and-ticks.md](blocks-and-ticks.md) |
| Random ticks | Done | [blocks-and-ticks.md](blocks-and-ticks.md) |
| Scheduled ticks | Done for gameplay path | [blocks-and-ticks.md](blocks-and-ticks.md) |
| Entity storage and packets | Implemented, vehicle manual validation pending | [entities.md](entities.md) |
| Player lifecycle canonicalization | Done for login, wake-up, and respawn | [entities.md](entities.md) |
| Entity tracking and spawning | Done for main paths | [entities.md](entities.md) |
| Mob despawn, sensing, pathfinding | Needs audit | [entities.md](entities.md) |
| Periodic terrain/noise | In progress | [worldgen.md](worldgen.md) |
| Structures and feature edge generation | Needs validation | [worldgen.md](worldgen.md) |
| Client chunk/world rendering | Implemented for server-relabeled aliases | [client.md](client.md) |
| Filled maps | Implemented for player marker aliasing | [maps.md](maps.md) |
| Curvature visuals | Implemented | [client.md](client.md) |
| Local solar time helper | Implemented | [local-solar-time.md](local-solar-time.md) |
| Day-length multiplier | Implemented | [scrolling-day-night.md](scrolling-day-night.md) |
| Scrolling day/night mode | Implemented | [scrolling-day-night.md](scrolling-day-night.md) |
| Scrolling sky/lightmap visuals | Implemented | [scrolling-day-night.md](scrolling-day-night.md) |
| Core local day/night gameplay | Implemented for sleep, monster/phantom spawning, undead burning | [scrolling-day-night.md](scrolling-day-night.md) |
| Secondary local day/night gameplay | Implemented for villager schedules, bees, turtle eggs, clocks, and patrol daylight gates | [scrolling-day-night.md](scrolling-day-night.md) |

## Current High-Risk Audits

1. Mob despawn: confirm `Mob.checkDespawn` and nearest-player lookup always use
   wrapped distance where a tile edge can make a nearby mob look far away.
2. Entity ticking: prove canonical mobs tick exactly once when only an alias is
   in entity-ticking range.
3. Cross-edge neighbor updates: redstone, pistons, observers, doors, and similar
   blocks need focused testing over tile boundaries.
4. Structures and feature origins: alias starts are transient worldgen data and
   structure query/persistence paths still need audit.
5. Terrain periodicity: tiny tiles are necessarily stylized; medium and large
   tiles need the right balance between seamlessness and vanilla-looking noise.
6. Scrolling day/night weather interaction: manually verify weather, lightning,
   night vision, and gamma with local sky/lightmap visuals.
