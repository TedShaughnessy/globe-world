# Globe World

Fabric mod for Minecraft 26.1.2 that makes the world tile seamlessly — walk far enough east and you reappear from the west, giving the illusion of a globe.

```
build:   ./gradlew build
client:  ./gradlew runClient
kill:    pkill -f runClient
```

## How it works

The world has a finite canonical tile of `W_CHUNKS × W_CHUNKS` chunks centered at the origin. Any chunk access outside that tile is transparently redirected to the canonical equivalent on the server. Outbound chunk and entity packets are relabeled to the player's virtual coordinate frame, so the client renders a continuous world with no seams and no client mod required.

Player coordinates grow unboundedly during a session. On death or world load the player is rebased to the canonical equivalent position.

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
| 2 | Planned | Periodic noise, biome and structure seam fix |
| 3 | Planned | Entity multiplayer — tracking, spawn/despawn, packet translation |
| 4 | Partial | Cosmetic shader bends terrain downward with a 0-100% world-creation curvature slider |
| 5 | Future | Hexagonal tiling, alternate tile sizes |
