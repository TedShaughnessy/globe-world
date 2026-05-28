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

## Status By Area

| Area | Status | Mechanic file |
| --- | --- | --- |
| Coordinate helpers | Done | [topology.md](topology.md) |
| Dimension-specific tiling | Implemented, needs validation | [topology.md](topology.md) |
| Chunk lookup and packet relabeling | Done | [chunks.md](chunks.md) |
| Canonical chunk lifetime | Done | [chunks.md](chunks.md) |
| Multiple rendered aliases | Done | [chunks.md](chunks.md) |
| Block edits and block packets | Done | [blocks-and-ticks.md](blocks-and-ticks.md) |
| Block entities | Mostly done | [blocks-and-ticks.md](blocks-and-ticks.md) |
| Random ticks | Done | [blocks-and-ticks.md](blocks-and-ticks.md) |
| Scheduled ticks | Done for gameplay path | [blocks-and-ticks.md](blocks-and-ticks.md) |
| Entity storage and packets | Done for mobs | [entities.md](entities.md) |
| Entity tracking and spawning | Done for main paths | [entities.md](entities.md) |
| Mob despawn, sensing, pathfinding | Needs audit | [entities.md](entities.md) |
| Periodic terrain/noise | In progress | [worldgen.md](worldgen.md) |
| Structures and feature edge generation | Needs validation | [worldgen.md](worldgen.md) |
| Client chunk/world rendering | Implemented for server-relabeled aliases | [client.md](client.md) |
| Curvature visuals | Implemented | [client.md](client.md) |

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
