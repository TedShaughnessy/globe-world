# Realistic Globe Lighting Plan

## Goal

Add an optional realistic day/night mode where the canonical globe tile's X axis acts like longitude. Local solar time should shift smoothly across the canonical tile and wrap by one full Minecraft day over one full tile width.

This should affect both presentation and gameplay. In other words, one side of
the globe can be locally night while the other side is locally day: sky visuals,
lightmap brightness, sleeping, monster spawning, monster burning, and similar
day/night predicates should use the local solar phase for their relevant block
or entity position.

This is intentionally semi-realistic:

- No axial tilt.
- No seasons.
- No poles.
- No latitude-dependent sun height.
- No discrete timezone bands.
- Z does not affect local time in the first version.

## Investigation Update

Minecraft 26.1.2 makes the visual half a moderate client-rendering feature,
not a light-engine rewrite. The settings/UI path is partly scaffolded already:
`DayNightCycleMode.REALISTIC` exists in
`src/main/java/globe/world/config/DayNightCycleMode.java`, is saved through
`TilingSettings.dayNightCycleMode()`, and is selectable in the world-creation
tab. The pause/options UI still only exposes curvature.

The larger feature has two parts:

- Client visuals: derive local day-track values without losing vanilla biome,
  weather, lightning, gamma, darkness, and night-vision behavior.
- Server gameplay: make day/night predicates position-aware so sleeping, mob
  spawning, mob burning, villager schedules, and similar mechanics can differ
  across the tile.

The visual half should still avoid server light-engine changes. The gameplay
half is more invasive because many vanilla callers assume one dimension-wide
day/night state.

New vanilla source notes:

- [Vanilla Time And Sky](../vanilla-mechanics/time-and-sky.md)
- `EnvironmentAttributeSystem.java:55` through `EnvironmentAttributeSystem.java:64`: dimension, biome, timeline, and weather layer order.
- `Timelines.java:43` through `Timelines.java:155`: built-in Overworld day tracks.
- `SkyRenderer.java:277` through `SkyRenderer.java:296`: sky render-state extraction from the camera probe.
- `LightmapRenderStateExtractor.java:44` through `LightmapRenderStateExtractor.java:83`: lightmap extraction from the camera probe.
- `Camera.java:86` through `Camera.java:90`: camera position updates the `EnvironmentAttributeProbe`.

## User-Facing Behavior

When realistic globe lighting is disabled, Minecraft keeps the normal global time-of-day visuals.

When realistic globe lighting is enabled:

- The local solar time is based on the camera/player/entity/block canonical X position.
- The middle of the canonical tile matches the world's normal time.
- The west and east edges are offset by half a Minecraft day in opposite directions.
- Moving along X changes sky, sun, moon, stars, and lightmap brightness smoothly.
- Crossing the canonical X seam should not create an obvious lighting jump, because both sides represent the same solar phase modulo one day.
- Sleeping eligibility is based on the bed/player's local night state, not only the dimension's global time.
- Monster spawning, monster burning, and similar light/day predicates use local solar phase at the checked block/entity position.
- Villager schedules, clock-like behavior, and other time predicates should be audited before declaring the feature complete.

## Saved Setting

This plan originally proposed a boolean such as:

```java
boolean realisticLightingEnabled
```

The current codebase has moved toward `DayNightCycleMode` instead:

- `VANILLA`
- `SCROLLING`
- `REALISTIC`

Prefer implementing realistic lighting behind `DayNightCycleMode.REALISTIC` unless we decide the mode enum and a separate boolean are both needed. The setting is already part of `TilingSettings.CODEC` as `day_night_cycle` and is carried by the existing `with...` helpers and `sanitized()`.

`GlobeConfig.dayNightCycleMode()` already exposes the enum. Add a convenience accessor only if it makes call sites clearer, for example:

```java
public static boolean realisticLightingEnabled()
```

No additional `with...` helper or `sanitized()` work is needed for the enum unless a separate boolean is introduced later.

## UI

Add a toggle in both existing globe UI surfaces:

- World creation screen, near the globe mode, tile size, and curvature controls. This is already present as the `Day/Night Cycle` cycle button.
- Pause/options screen, visible only for enabled globe worlds, next to the curvature slider. This is still missing.

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

Important implementation constraints from the source audit:

- Vanilla time-based layers cannot see position. `EnvironmentAttributeLayer.TimeBased.applyTimeBased(...)` receives only the base value and cache tick id.
- Positional layers can see the camera position, but layer ordering matters. A Globe layer inserted after the Overworld day timeline and before `WeatherAttributes.addBuiltinLayers(...)` can let weather continue to modify the locally computed sky.
- A layer added at the end of `ClientLevel.addEnvironmentAttributeLayers(...)` is easier to hook but sees already-weathered values, so replacing values there risks undoing rain/thunder/flash effects.
- `Level.updateSkyBrightness()` reads `SKY_LIGHT_LEVEL` with `getDimensionValue(...)`, which ignores positional layers. Keep that global for the visual-only MVP.

Recommended visual hook shape:

1. Add a client helper, for example `GlobeLocalSolarTime`, that computes canonical-X local ticks and samples or mirrors the vanilla Overworld day curves for the visual attributes.
2. Add client-only mixins that install Globe positional environment layers when `GlobeConfig.enabled()` and `GlobeConfig.dayNightCycleMode() == DayNightCycleMode.REALISTIC`.
3. Prefer an insertion near `EnvironmentAttributeSystem.addDefaultLayers(...)` before weather layers. If that proves too brittle, fall back to targeted redirects in `SkyRenderer.extractRenderState(...)` and `LightmapRenderStateExtractor.extract(...)`.

## Gameplay Hook

The intended feature should make local solar time real enough for vanilla
day/night gameplay. This does not necessarily mean physically rewriting sky
light section propagation in the first version. It does mean auditing and
hooking predicates that currently collapse time to one dimension-wide value.

Important gameplay surfaces:

- Position-aware sky brightness queries. Vanilla commonly combines stored sky
  light with `Level.getSkyDarken()`, which is global.
- `Level.getSkyDarken()`, `Level.isBrightOutside()`, and
  `Level.isDarkOutside()`, which assume one sky-darkening value for the whole
  level.
- Monster spawn rules and despawn/burn checks that query local raw brightness,
  global day/night, or environment attributes such as `MONSTERS_BURN`.
- Sleeping and bed rules, especially checks that ask whether it is night or
  whether the player can sleep now.
- Villager schedules, bee behavior, raids/patrols, turtle eggs, cat gifts,
  eyeblossoms, creaking, and other timeline-driven gameplay attributes listed
  in the vanilla Overworld day timeline.
- Clock item behavior and command/time predicates if they are expected to show
  or test local time.

Recommended gameplay hook shape:

1. Add shared local-time helpers in `CoordUtil` or a small companion utility:
   local day ticks, local sky darken, local is-day/is-night predicates, and
   optionally local timeline marker predicates.
2. Start with the high-value gameplay contracts: sleeping, monster spawning,
   and monster burning.
3. Add focused vanilla source notes for each audited gameplay path before
   adding mixins.
4. Keep server block light and stored sky light propagation global unless a
   later phase proves that predicate hooks are insufficient.

## First Implementation Scope

The first implementation should be end-to-end for the core fantasy:

- Adjust sun, moon, and star angles.
- Adjust star brightness from local solar time.
- Adjust sky/lightmap brightness from local solar time.
- Make sleeping use local night.
- Make monster spawning use local darkness/day state.
- Make monster burning use local day state.
- Keep weather effects working.
- Keep the server's global `dayTime` unchanged.

Leave broader secondary systems global until separately audited:

- Villager schedules.
- Crop/random tick behavior.
- Clock item behavior.
- Server block light or sky light propagation.

## Later Expansion

After the core gameplay pass, expand to villager schedules, bee behavior,
timeline-driven block/entity rules, clocks, commands, and any mechanics where a
global day/night assumption is visibly wrong on a map with simultaneous day and
night regions.

## Documentation Updates

Update:

- `docs/mod-mechanics/client.md` with a client visuals status note.
- `docs/vanilla-mechanics/README.md` if a new mechanics note is added.

Added:

- `docs/vanilla-mechanics/time-and-sky.md`

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
- A bed in a local-night region allows sleep while a bed in a local-day region does not.
- Hostile mob spawning follows local darkness/day state on opposite sides of the tile.
- Undead burning follows local day state on opposite sides of the tile.
- Curvature and realistic lighting work together.
- Weather, lightning flashes, night vision, and gamma still look reasonable.
