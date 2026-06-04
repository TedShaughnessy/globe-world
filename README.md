# Globe World

Fabric mod for Minecraft 26.1.2 that makes the world tile seamlessly — walk far enough east and you reappear from the west, giving the illusion of a globe.

```
build:             ./gradlew build
client:            ./gradlew runClient --debug > debug_log.txt 2>&1
kill:              pkill -f runClient
simple shaderpack: ./gradlew packageGlobeWorldCurvatureShaderpack
makeup shaderpack: ./gradlew packageMakeupUltraFastGlobeWorldShaderpack
all shaderpacks:   ./gradlew shaderpacks
```

## Repository layout

Globe World is organized as a small monorepo. The root Gradle project is an
aggregator, and the current Fabric mod lives in `mod-fabric/`.

```
mod-fabric/
  build.gradle
  src/main/      shared/server-side mod sources and resources
  src/client/    client-only sources and resources
```

Root tasks such as `./gradlew build` and `./gradlew runClient` delegate to the
Fabric module so day-to-day commands can still be run from the repository root.

Shader pack tasks write zips to `build/distributions/shaderpacks/`. The MakeUp
Ultra Fast task fetches the pinned upstream commit listed in
`shaderpacks/makeup-ultra-fast-globe-world/upstream.properties`, applies the
local patches in `shaderpacks/makeup-ultra-fast-globe-world/patches/`, and then
packages the patched shader pack.

Release artifact versions live in `gradle.properties`. Bump `mod_version`,
`globe_world_curvature_shaderpack_version`, or
`makeup_ultra_fast_globe_world_shaderpack_version` to make the GitHub Actions
workflow upload that specific build artifact after the next push. Publishing a
GitHub Release stays manual through the workflow dispatch inputs.

## How it works

The world has a finite canonical tile of `W_CHUNKS × W_CHUNKS` chunks centered at the origin. Any chunk access outside that tile is transparently redirected to the canonical equivalent on the server. Outbound chunk and entity packets are relabeled to the player's virtual coordinate frame, so the client renders a continuous world with no seams and no client mod required.

Player coordinates grow unboundedly during a session. On death, bed wake-up, or world load the player is rebased to the canonical equivalent position.

## What is and isn't changed

**Changed:**
- Chunk lookup and packet labeling (server → client)
- Entity spawn/teleport packets (coordinate translation to player's virtual frame)
- Entity tracking range (wrapped XZ distance)
- Mob spawn and despawn distance checks (wrapped XZ distance)
- Block update packets sent to players viewing virtual tile positions
- Scheduled block/fluid ticks canonicalized for the normal gameplay path
- Immediate redstone dust shape updates render through aliases

**Not changed:**
- Gravity, physics, collision
- Terrain generation algorithm (noise is made periodic in Phase 2)
- Random block ticks, inventories, and full redstone/piston/observer edge-case behavior

## Phases

| Phase | Status | Description |
|---|---|---|
| 1 | Done | Core chunk wrapping — terrain repeats, no seams yet |
| 2 | Done | Periodic noise, biome and structure seam fix |
| 3 | Planned | Entity multiplayer — tracking, spawn/despawn, packet translation |
| 4 | Done | Cosmetic shader bends terrain downward with a 0-100% world-creation curvature slider |
