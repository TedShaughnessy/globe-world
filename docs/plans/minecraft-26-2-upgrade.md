# Minecraft 26.2 Upgrade Plan

This plan tracks the work to port Globe World from Minecraft 26.1.2 to
Minecraft 26.2 without losing the audited wrapping behavior.

Checked against Fabric metadata on 2026-06-23:

- Fabric Loader has a stable `0.19.3` entry for Minecraft 26.2.
- Fabric API has 26.2 builds through `0.153.0+26.2`.
- Fabric Loom metadata lists `1.17.12` as the latest release and
  `1.17-SNAPSHOT` as the latest snapshot.

Keep the target at Minecraft 26.2 for this plan. Fabric API metadata already
contains a 26.3 build, so treat any jump to 26.3 as a separate retargeting
decision.

## Current Pins

Current values in `gradle.properties`:

```properties
minecraft_version=26.1.2
loader_version=0.19.2
loom_version=1.16-SNAPSHOT
java_version=25
fabric_api_version=0.149.0+26.1.2
```

Likely first 26.2 trial pins:

```properties
minecraft_version=26.2
loader_version=0.19.3
loom_version=1.17.12
fabric_api_version=0.153.0+26.2
```

If Loom source generation or remapping fails with the release plugin, retry with
`loom_version=1.17-SNAPSHOT` before changing code.

## Expected Issues

Expect some issues. This repository has a broad mixin surface, and most risk is
not in Fabric API usage but in injections, redirects, accessors, shadows, and
targeted vanilla control flow.

Highest-risk areas:

- Mixin target drift in server internals: chunk map, chunk holders, chunk
  sending, block updates, tick containers, entity tracking, spawning, portals,
  explosions, and structure/worldgen classes.
- Client renderer drift: sky, clouds, level renderer, occlusion graph, frustum,
  shader loading, debug renderer, create-world screens, and packet listener
  code are common rename or refactor points.
- Packet shape changes: the packet policy table is explicitly audited for
  Minecraft 26.1.2, so every position-bearing clientbound packet needs a fresh
  26.2 pass.
- Worldgen and terrain periodicity: density functions, noise, surface system,
  structure placement, feature spillover, and direct mutation paths may compile
  while still changing behavior.
- Sodium/Iris compatibility: the Sodium plugin currently hard-pins
  `0.8.12+mc26.1.2`, so the Sodium-specific culling mixins will intentionally
  disable until a 26.2 Sodium target is audited.
- Documentation drift: README, development notes, vanilla source paths, packet
  policy docs, and mod-mechanics status tables all name Minecraft 26.1.2.

Lower-risk areas:

- Central Gradle properties and artifact naming are already version-driven.
- `fabric.mod.json` expands dependency versions from Gradle properties.
- Java remains pinned to 25 unless Minecraft 26.2 or the launcher metadata says
  otherwise.

## Upgrade Steps

1. Confirm final dependency pins from official Fabric metadata.
2. Bump `minecraft_version`, `loader_version`, `loom_version`, and
   `fabric_api_version` in `gradle.properties`.
3. Ask the user to run `./gradlew :mod-fabric:genSources` from the repository
   root so Loom generates the 26.2 common and client-only source jars.
4. Update `AGENTS.md`, `DEVELOPMENT.md`, and
   `docs/vanilla-mechanics/README.md` with the new local Loom source paths.
5. Ask the user to run `./gradlew build`.
6. Fix compile errors first, grouped by subsystem:
   chunk/packet plumbing, worldgen/noise, entity/query behavior, client render,
   UI/config, then optional compatibility mixins.
7. Once the build compiles, run a mixin audit against 26.2 sources:
   verify every `@Inject`, `@Redirect`, `@ModifyArg`, `@ModifyVariable`,
   `@ModifyConstant`, `@WrapOperation`, `@WrapMethod`, `@Accessor`,
   `@Invoker`, and `@Shadow` still targets the intended code.
8. Rebuild the packet policy table from 26.2 sources and update
   `docs/vanilla-mechanics/position-bearing-packets.md` and
   `docs/mod-mechanics/packet-policies.md`.
9. Recheck the vanilla mechanics pages whose source anchors mention 26.1.2:
   chunk loading, client world, block updates, scheduled ticks, random ticks,
   lighting, mobs/entities, worldgen, game events, and time/sky.
10. Update player-facing docs and release naming examples in `README.md`.
11. Audit Sodium and Iris versions for 26.2:
    leave Sodium-specific mixins disabled unless a matching Sodium version has
    been inspected, then update `SUPPORTED_SODIUM_VERSION` and compatibility
    docs.
12. Ask the user to run `./gradlew buildAll` after code and docs are updated.

## Runtime Validation

Use a small tile and a medium tile. Test X seam, Z seam, and corner seam.

Core checks:

- Create a Globe World and verify the Globe settings tab still persists through
  world creation, world recreation, pause-menu edits, and multiplayer settings
  sync.
- Walk, fly, boat, minecart, teleport, respawn, and nether-portal travel across
  seams.
- Place and break blocks across seams, including redstone, pistons, observers,
  doors, block entities, fluids, scheduled ticks, and random ticks.
- Confirm chunk aliases load, unload, relabel, and receive block/light/entity
  updates without stale client chunks.
- Test entity tracking, visual aliases, despawn ranges, projectiles, melee,
  ranged mobs, pets, villagers, raids, POI lookup, hives, and beds across seams.
- Test explosions, wind charges, sculk sensors, shriekers, allays, and wardens
  near seams.
- Generate structures and features near seams, including forced progression
  structures and the End portal fallback.
- Verify scrolling day/night visuals and gameplay hooks: sleep, monster
  spawning, phantom spawning, undead burning, villager schedules, bees, clocks,
  patrol gates, and weather/lightning.
- Verify filled maps, waypoints, lodestones, compasses, debug overlay, and
  tile-border renderer.

Client/render checks:

- Vanilla renderer: terrain curvature, sky, clouds, fog, frustum behavior,
  hand/item/entity/fishing-line rendering, and debug overlays.
- Sodium without Iris: wrapping should work; built-in curvature remains limited.
- Sodium with Iris: optional shader packs should apply curvature only after the
  Iris bridge and Sodium culling mixins have been re-audited for 26.2.
- Distant Horizons: confirm documented caveats still match observed behavior.

## Done Criteria

- `./gradlew build` passes when the user runs it.
- `./gradlew buildAll` produces mod and shader-pack artifacts named with
  `mc26.2`.
- All required mixins apply cleanly in a dev client and do not log unexpected
  target failures.
- Packet policy docs and vanilla source anchors are updated for Minecraft 26.2.
- The runtime validation checklist has no untriaged regressions.
- Any remaining 26.2 limitations are moved into `known-issues.md`,
  `docs/mod-compatibility/`, or a follow-up plan.
