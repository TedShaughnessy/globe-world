# Realistic Globe Lighting Plan

## Goal

Add an optional visual lighting mode where the canonical globe tile's X axis acts like longitude. Local solar time should shift smoothly across the canonical tile and wrap by one full Minecraft day over one full tile width.

This is intentionally semi-realistic:

- No axial tilt.
- No seasons.
- No poles.
- No latitude-dependent sun height.
- No discrete timezone bands.
- Z does not affect local time in the first version.

## User-Facing Behavior

When realistic globe lighting is disabled, Minecraft keeps the normal global time-of-day visuals.

When realistic globe lighting is enabled:

- The local visual time is based on the camera/player's canonical X position.
- The middle of the canonical tile matches the world's normal time.
- The west and east edges are offset by half a Minecraft day in opposite directions.
- Moving along X changes sky, sun, moon, stars, and lightmap brightness smoothly.
- Crossing the canonical X seam should not create an obvious lighting jump, because both sides represent the same solar phase modulo one day.

## Saved Setting

Extend `TilingSettings` with a boolean such as:

```java
boolean realisticLightingEnabled
```

Add it to the worldgen codec next to `curvature_percent`, with a default of `false` unless we decide this should be part of the globe-world default preset.

Add convenience accessors to `GlobeConfig`, for example:

```java
public static boolean realisticLightingEnabled()
```

Update `with...` helpers and `sanitized()` so the setting survives normal config updates.

## UI

Add a toggle in both existing globe UI surfaces:

- World creation screen, near the globe mode, tile size, and curvature controls.
- Pause/options screen, visible only for enabled globe worlds, next to the curvature slider.

The pause/options toggle should update via `GlobeClientTilingSettings.setFromPauseMenu(...)`, matching the curvature slider's live-update pattern.

## Longitude Math

Keep the coordinate math in one place, probably `CoordUtil`, so rendering hooks do not duplicate tile wrapping details.

Proposed behavior:

```text
canonicalX = wrapBlock(cameraX)
tileSize = GlobeConfig.tileSizeBlocks()
longitudeFraction = canonicalX / tileSize
offsetTicks = longitudeFraction * 24000
localDayTime = worldDayTime + offsetTicks
```

Because canonical X is centered around zero:

- West side is approximately `-12000` ticks.
- Center is `0` ticks.
- East side is approximately `+12000` ticks.

The result should be normalized modulo `24000` before converting to angles or brightness.

## Hexagonal Tiling Note

A future hexagonal tiling mode can keep the same simple local-time model if the tiling is oriented deliberately.

Use a hex layout with flat faces stacked north to south, then treat the perpendicular axis as the east-west longitude axis. Timezones/local solar offsets should vary smoothly along that perpendicular axis, not along the north-south stack axis.

This preserves the lightweight projection model:

- North-south movement mostly preserves local solar time.
- East-west movement advances or rewinds local solar time.
- No full sun-position simulation is required for the first hex implementation.

## Client Rendering Hook

Minecraft 26.1.2 drives sky and lightmap visuals through environment attributes. The first implementation should target client visuals, using the camera/player position to sample local solar time.

Important vanilla surfaces:

- `SkyRenderer.extractRenderState(...)`
  - `EnvironmentAttributes.SUN_ANGLE`
  - `EnvironmentAttributes.MOON_ANGLE`
  - `EnvironmentAttributes.STAR_ANGLE`
  - `EnvironmentAttributes.STAR_BRIGHTNESS`
  - `EnvironmentAttributes.SUNRISE_SUNSET_COLOR`
  - `EnvironmentAttributes.SKY_COLOR`
- `LightmapRenderStateExtractor.extract(...)`
  - `EnvironmentAttributes.SKY_LIGHT_FACTOR`
  - `EnvironmentAttributes.SKY_LIGHT_COLOR`
  - `EnvironmentAttributes.AMBIENT_LIGHT_COLOR`

The preferred implementation is a positional layer or small client-side helper that derives adjusted values from local solar time while preserving biome/weather/lightning modifiers where practical.

## MVP Scope

The first pass should be visual-only:

- Adjust sun, moon, and star angles.
- Adjust star brightness from local solar time.
- Adjust sky/lightmap brightness from local solar time.
- Keep weather effects working.
- Keep the server's global `dayTime` unchanged.

Do not change gameplay rules in the first pass. In particular, leave these global unless a later phase explicitly tackles them:

- Mob burning.
- Mob spawning.
- Sleeping rules.
- Villager schedules.
- Crop/random tick behavior.
- Clock item behavior.
- Server block light or sky light propagation.

## Later Gameplay Phase

If realistic lighting should affect gameplay later, audit and plan separate hooks for:

- Position-aware sky brightness queries.
- `Level.getSkyDarken()` and callers that assume one dimension-wide value.
- Monster burn checks.
- Sleeping and bed rules.
- Clocks and time predicates.
- Mob spawning/despawning behavior around dark/light local regions.

This is a larger gameplay change and should not be bundled into the initial visual feature.

## Documentation Updates

Update:

- `docs/globe-world-system-plan.md` with a client visuals row/status note.
- `docs/minecraft-mechanics/README.md` if a new mechanics note is added.

Consider adding:

- `docs/minecraft-mechanics/time-and-sky.md`

That note should reference the vanilla environment attribute system and the client sky/lightmap extraction points.

## Verification

Run:

```sh
./gradlew build
```

Manual client checks:

- Realistic lighting disabled keeps vanilla/global time visuals.
- Realistic lighting enabled changes local sky time smoothly as the player moves along X.
- Moving along Z does not change local solar time.
- Crossing the canonical X seam has no obvious visual pop.
- Curvature and realistic lighting work together.
- Weather, lightning flashes, night vision, and gamma still look reasonable.
