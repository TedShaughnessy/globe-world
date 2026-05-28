# Realistic Globe Lighting Plan

## Goal

Add an optional scrolling day/night mode where the canonical globe tile's X axis acts like longitude. Local solar time should shift smoothly across the canonical tile and wrap by one full Minecraft day over one full tile width.

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
`DayNightCycleMode.SCROLLING` exists in
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

When scrolling globe lighting is disabled, Minecraft keeps the normal global time-of-day visuals.

When scrolling globe lighting is enabled:

- The local solar time is based on the camera/player/entity/block canonical X position.
- The middle of the canonical tile matches the world's normal time.
- The west and east edges are offset by half a Minecraft day in opposite directions.
- Moving along X changes sky, sun, moon, stars, and lightmap brightness smoothly.
- Crossing the canonical X seam should not create an obvious lighting jump, because both sides represent the same solar phase modulo one day.
- Sleeping eligibility is based on the bed/player's local night state, not only the dimension's global time.
- Monster spawning, monster burning, and similar light/day predicates use local solar phase at the checked block/entity position.
- Villager schedules, bees, turtle eggs, clocks, and patrol daylight gates use
  local time; command/time predicates and remaining timeline consumers should
  still be audited before declaring the feature complete.

## Saved Setting

This plan originally proposed a boolean such as:

```java
boolean realisticLightingEnabled
```

The current codebase uses `DayNightCycleMode`:

- `VANILLA`
- `SCROLLING`

Implement local solar visuals and gameplay behind `DayNightCycleMode.SCROLLING`.
The setting is already part of `TilingSettings.CODEC` as `day_night_cycle` and
is carried by the existing `with...` helpers and `sanitized()`. The codec accepts
legacy saved `"realistic"` values as `SCROLLING`.

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

The shared coordinate math is implemented in `CoordUtil`, so rendering and
gameplay hooks do not duplicate tile wrapping details.

Implemented behavior:

```text
canonicalX = wrapBlock(cameraX)
longitudeOffsetTicks = canonicalX / tileSizeBlocks * 24000
localSolarTimeTicks = worldTime + longitudeOffsetTicks
localSolarDayTicks = localSolarTimeTicks mod 24000
```

Because canonical X is centered around zero:

- West side is approximately `-12000` ticks.
- Center is `0` ticks.
- East side is approximately `+12000` ticks.

`CoordUtil.localSolarTimeTicks(...)` keeps the monotonic world-time component.
`CoordUtil.localSolarDayTicks(...)` normalizes the result modulo `24000` before
conversion to angles, brightness, or day/night predicates.

Step 1 status: implemented. Remaining steps should consume these helpers
instead of recomputing longitude offsets.

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
2. Add client-only mixins that install Globe positional environment layers when `GlobeConfig.enabled()` and `GlobeConfig.dayNightCycleMode() == DayNightCycleMode.SCROLLING`.
3. Prefer an insertion near `EnvironmentAttributeSystem.addDefaultLayers(...)` before weather layers. If that proves too brittle, fall back to targeted redirects in `SkyRenderer.extractRenderState(...)` and `LightmapRenderStateExtractor.extract(...)`.

Step 2 status: implemented with `GlobeScrollingSky` and
`EnvironmentAttributeSystemBuilderMixin`. The client replaces the Overworld day
timeline for sky/lightmap visual attributes with camera-position-aware
positional layers, while weather and lightning layers still run afterward.

Visual follow-up status: first pass implemented with `GlobeSkyHorizon` and
`SkyRendererMixin`. When the curvature shader is enabled, the client derives a
camera-relative horizon dip from the same curvature radius used by the terrain
shader and shifts the sky disc, lower dark disc, sunrise/sunset fan, sun, moon,
and stars downward. The cloud shader also uses the same curvature transform as
terrain so vanilla's flat cloud layer bends with the world presentation. This
still needs in-game tuning across sea level, mountains, tiny tiles, fog, and
large render distances.

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
- Villager schedules, bee behavior, patrols, turtle eggs, cat gifts,
  eyeblossoms, creaking, and other timeline-driven gameplay attributes listed
  in the vanilla Overworld day timeline.
- Clock item behavior and command/time predicates if they are expected to show
  or test local time. Clock item daytime display is already position-aware
  through the local `SUN_ANGLE` visual layer.

Recommended gameplay hook shape:

1. Use the shared local-time helpers in `CoordUtil`. Local sky darken,
   local is-day/is-night predicates, and optional local timeline marker
   predicates can be added on top as the audited gameplay hooks need them.
2. Start with the high-value gameplay contracts: sleeping, monster spawning,
   and monster burning.
3. Add focused vanilla source notes for each audited gameplay path before
   adding mixins.
4. Keep server block light and stored sky light propagation global unless a
   later phase proves that predicate hooks are insufficient.

Step 3 status: implemented for core and audited secondary predicates with
`GlobeLocalDaylight`, `PlayerLocalSleepMixin`, `ServerPlayerLocalSleepMixin`,
`ServerLevelLocalSleepTimeMixin`, `MonsterLocalDaylightMixin`,
`PhantomSpawnerLocalDaylightMixin`, `MobLocalDaylightMixin`,
`EnvironmentAttributeSystemBuilderGameplayMixin`, and
`PatrolSpawnerLocalDaylightMixin`.

- Sleeping keeps vanilla bed rules, but evaluates `WHEN_DARK` at the bed/player
  position in scrolling mode.
- Sleep time skipping still advances the shared default clock, but targets a
  sleeping player's local morning. In multiplayer, the chosen sleeper is the one
  furthest from their next local morning.
- Monster natural spawning keeps vanilla sky/block light tests, but the raw
  brightness check uses local sky darkening at the spawn position.
- Phantom spawning keeps vanilla player/rest/sky checks, but the global
  darkening gate becomes a local check at each player position.
- Undead burning keeps vanilla sky visibility, weather/water protection, and
  helmet behavior, but evaluates both the burn-time predicate and brightness at
  the mob position.
- Villager schedules, baby villager schedules, bee hive behavior, turtle egg
  hatch chance, cat gifts, eyeblossoms, creaking, and direct `MONSTERS_BURN`
  environment-attribute callers use local positional timeline layers.
- Clock item daytime display already samples `SUN_ANGLE` at the item owner
  position, so it follows the local client sky layer.
- Patrol spawning opens the global bright-outside gate in scrolling mode, then
  requires local daylight at the chosen patrol spawn position. Raids do not
  have a vanilla day/night start gate in 26.1.2; villagers leaving raid
  activities resume through the localized schedule.

Future tuning question: confirm whether the "furthest from local morning"
multiplayer rule feels right, or whether sleep skip should instead pick a
different sleeper such as the one with the largest longitude offset.

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

- Crop/random tick behavior.
- Command/time predicates.
- Server block light or sky light propagation.

## Later Expansion

After the secondary gameplay pass, expand to commands and any remaining
mechanics where a global day/night assumption is visibly wrong on a map with
simultaneous day and night regions.

## Documentation Updates

Update:

- `docs/mod-mechanics/client.md` with a client visuals status note.
- `docs/mod-mechanics/local-solar-time.md` with core gameplay predicate status.
- `docs/vanilla-mechanics/README.md` if a new mechanics note is added.

Added:

- `docs/vanilla-mechanics/time-and-sky.md`

## Verification

Run:

```sh
./gradlew build
```

Manual client checks:

- Scrolling lighting disabled keeps vanilla/global time visuals.
- Scrolling lighting enabled changes local sky time smoothly as the player moves along X.
- Moving along Z does not change local solar time.
- Crossing the canonical X seam has no obvious visual pop.
- A bed in a local-night region allows sleep while a bed in a local-day region does not.
- Hostile mob spawning follows local darkness/day state on opposite sides of the tile.
- Undead burning follows local day state on opposite sides of the tile.
- Curvature and scrolling lighting work together.
- With curvature enabled, the sky horizon lines up with the curved terrain
  horizon or the mismatch is intentionally compensated.
- Weather, lightning flashes, night vision, and gamma still look reasonable.
