# Vanilla Mechanics Reference

This folder is a progressive-disclosure index for vanilla Minecraft mechanics that are likely to matter when changing world topology, coordinate identity, chunk identity, or cross-boundary behavior.

It is intentionally about vanilla mechanics first. Use it to find the relevant decompiled source files before deciding where a project-specific change belongs.

If you discover more important references, update these docs.

## Source Roots

Common/server/shared sources:

`.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-52430b475d/26.1.2/minecraft-common-52430b475d-26.1.2-sources.jar`

Client-only sources:

`.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-52430b475d/26.1.2/minecraft-clientOnly-52430b475d-26.1.2-sources.jar`

Source references below use jar-internal paths such as:

`net/minecraft/server/level/ServerChunkCache.java:148`

## Start Here

1. [Chunk Loading](chunk-loading.md): chunk tickets, chunk holders, chunk packet send/drop, server view tracking.
2. [Block Updates](block-updates.md): `Level.setBlock`, neighbor updates, block update packets, redstone-adjacent update flow.
3. [Scheduled Ticks](scheduled-ticks.md): queued block/fluid ticks, per-chunk tick containers, clone/copy helpers.
4. [Fluids](fluids.md): liquid scheduling, spreading, flow-vector lookup, lava/water interactions.
5. [Lighting](lighting.md): block/sky light engines, section data, light update packets.
6. [Random Ticks](random-ticks.md): random block/fluid ticks during chunk ticking.
7. [Block Entities](block-entities.md): block entity storage, ticking, save/load, update packets.
8. [Mobs And Entities](mobs-and-entities.md): entity ticking, entity chunk storage, natural spawning, tracking.
9. [World Generation](world-generation.md): chunk status pipeline, biomes, noise, features, structures. Deep dive: [Structure Edge Generation](structure-edge-generation.md).
10. [Client World](client-world.md): client chunk cache, chunk/light/block packet application, render-facing storage.
11. [Position-Bearing Packets](position-bearing-packets.md): clientbound game packets with block, chunk, entity, sound, particle, waypoint, or player coordinates.
12. [Time And Sky](time-and-sky.md): environment attributes, timelines, camera sampling, sky, and lightmap extraction.

## Reading Pattern

For any mechanic:

1. Read the page overview.
2. Open the listed source files in the source jar.
3. Follow the "entry points" first.
4. Use the "audit questions" to decide whether the mechanic depends on absolute block position, chunk position, section position, player distance, or packet coordinates.
