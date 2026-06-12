# Scrolling Day/Night

## What

Scrolling day/night mode treats the canonical tile's X axis as longitude. In
`DayNightCycleMode.SCROLLING`, local solar time shifts smoothly across the tile
and wraps by one Minecraft day over one tile width.

The feature is intentionally semi-realistic:

- X affects local solar time.
- Z does not affect local solar time.
- There is no axial tilt, seasons, poles, or discrete timezone banding.
- The server's shared world clock stays monotonic and global.

## Why

Globe World's tile can show multiple apparent longitudes at once. A single
global day/night presentation makes the whole tile day or night together, which
does not match the globe fantasy. Local solar time lets one side of the
canonical tile be day while the other side is night without rewriting world
clock ownership or server light propagation.

## Settings

The saved setting is `GlobeSettings.gameplay().dayNightCycleMode()`:

- `VANILLA`: normal global Minecraft time-of-day behavior.
- `SCROLLING`: local solar time is derived from canonical X.

The setting is serialized as `day_night_cycle` inside the saved
`globe_world.gameplay` group; legacy saved `"realistic"` values decode as
`SCROLLING`. The world-creation UI and pause/options Globe World settings page
expose the setting as `Day/Night Cycle`.
In simple world-creation mode, scrolling day cycle is disabled and reset to
`VANILLA` when the Overworld tile is below 7,000 blocks wide. At that scale a
running player can approximately keep pace with the sun, so the simple preset UI
keeps local-solar-time behavior off.

Day length is saved separately as
`GlobeSettings.gameplay().dayLengthMultiplier()` and serialized as
`day_length_multiplier` in the same gameplay group. The UI exposes a discrete
`Day Length` slider with `0.5x`, then `1x` through `10x`. This multiplier
controls how long the Overworld day lasts in both `VANILLA` and `SCROLLING`
day/night modes: `1x` uses vanilla speed, `2x` takes twice as long, and `0.5x`
takes half as long. `GlobeDayLength` applies the inverse multiplier to
vanilla's Overworld clock rate, matching the `/time rate` command model.

`/globeworld config set day_night <vanilla|scrolling>` and
`/globeworld config set day_length <0.5-10>` update these saved settings at
runtime. Day-length command values up to `0.75` are sanitized to `0.5`; larger
values are sanitized to whole-number multipliers by `GameplaySettings`.

## Longitude Math

The shared coordinate math lives in `CoordUtil`:

```text
canonicalX = wrapBlock(x)
longitudeOffsetTicks = canonicalX / tileSizeBlocks * 24000
localSolarTimeTicks = worldTime + longitudeOffsetTicks
localSolarDayTicks = localSolarTimeTicks mod 24000
```

The middle of the canonical tile matches the global clock. The west and east
sides are approximately half a Minecraft day behind and ahead of the global
clock, and the wrapped X seam maps back to the same solar phase modulo one day.

## Client Visuals

Minecraft 26.1.2 drives most sky and lightmap visuals through environment
attributes. `GlobeScrollingSky` replaces the Overworld visual day timeline with
mode-aware layers in Globe worlds. The layers sample the vanilla global clock
while `DayNightCycleMode.VANILLA` is active and switch to local solar time while
`DayNightCycleMode.SCROLLING` is active, so pause/options mode changes apply to
the existing loaded level.

Locally sampled visual attributes include:

- Sun, moon, and star angles.
- Star brightness.
- Sunrise/sunset color.
- Sky color.
- Fog color.
- Cloud color.
- Sky light factor.
- Sky light color.

Weather layers run after the local visual layer, so they can still modify the
locally computed sky, fog, clouds, and lightmap. Lightning flash layers run
after those weather layers on the client.

Curvature has separate client presentation hooks. `GlobeCurvatureShader` bends
terrain and cloud vertices downward in relevant world vertex shaders.
`GlobeSkyHorizon` and `SkyRendererMixin` apply a camera-relative vertical
offset to the sky disc, lower dark disc, sunrise/sunset fan, sun, and moon so
the sky horizon better matches the curved terrain horizon. Sun and moon render
on a larger effective sky sphere, preserving their apparent size while reducing
the offset's midday and midnight angular distortion. The mixin applies these
offsets through copied uniforms or vanilla's existing model-view stack frame so
shader-pack render hooks do not spend an extra global matrix-stack slot. Stars
stay on the vanilla dome. Cloud curvature uses a larger radius than terrain so
clouds fall away more gently. Orthographic GUI projections are exempt from
shader curvature, so inventory entity previews and special item models stay
flat.

The vanilla directional sunrise/sunset fan is still rendered. This means dusk
color can vary by whether the player faces the sun or moon; that behavior is
accepted for now.

## Gameplay

`GlobeLocalDaylight` builds local day/night predicates and local timeline
samplers on top of `CoordUtil`. The feature is active only for tiled skylight
dimensions without fixed time.

Implemented gameplay behavior:

- Beds evaluate `BedRule.WHEN_DARK` at the bed/player position.
- Sleep time skipping still advances the shared default clock, but targets a
  sleeping player's next local morning. In multiplayer, the chosen sleeper is
  the one furthest from local morning.
- Monster natural spawning keeps vanilla sky/block light tests, but the raw
  brightness check uses local sky darkening at the spawn position.
- Phantom spawning keeps vanilla player/rest/sky checks, but the global
  darkening gate becomes local at each player position.
- Undead burning keeps vanilla sky visibility, water, rain, powder snow, and
  helmet behavior, but evaluates burn-time and brightness at the mob position.
- Villager schedules, baby villager schedules, bee hive behavior, turtle egg
  hatch chance, cat gifts, eyeblossoms, creaking, and direct
  `MONSTERS_BURN` environment-attribute callers use local positional timeline
  layers.
- Clock item daytime display samples `SUN_ANGLE` at the item owner position, so
  it follows the local client sky layer.
- Patrol spawning opens vanilla's global bright-outside gate in scrolling mode,
  then requires local daylight at the chosen patrol spawn position.

Stored sky light propagation remains global. Local gameplay predicates supply
position-aware darkening at their call sites instead of rewriting the light
engine.

## Boundaries

These systems intentionally remain global or are accepted for now:

- Server block light and stored sky light propagation.
- Crop/random tick behavior.
- Commands and generic time predicates.
- Manual `/time rate` changes can be overwritten by the saved Globe World day
  length setting when a world loads or the pause/options day-length setting
  changes.
- Dusk's vanilla direction-dependent sunrise/sunset fan.

## Key Files

- `mod-fabric/src/main/java/globe/world/config/DayNightCycleMode.java`
- `mod-fabric/src/main/java/globe/world/config/GameplaySettings.java`
- `mod-fabric/src/main/java/globe/world/config/GlobeSettings.java`
- `mod-fabric/src/main/java/globe/world/util/GlobeDayLength.java`
- `mod-fabric/src/main/java/globe/world/util/CoordUtil.java`
- `mod-fabric/src/main/java/globe/world/util/GlobeLocalDaylight.java`
- `mod-fabric/src/client/java/globe/world/client/GlobeScrollingSky.java`
- `mod-fabric/src/client/java/globe/world/client/GlobeSkyHorizon.java`
- `mod-fabric/src/client/java/globe/world/client/GlobeCurvatureShader.java`
- `mod-fabric/src/client/java/globe/world/client/mixin/EnvironmentAttributeSystemBuilderMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/EnvironmentAttributeSystemBuilderGameplayMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/PlayerLocalSleepMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ServerPlayerLocalSleepMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ServerLevelLocalSleepTimeMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/MonsterLocalDaylightMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/PhantomSpawnerLocalDaylightMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/MobLocalDaylightMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/PatrolSpawnerLocalDaylightMixin.java`
- `mod-fabric/src/client/java/globe/world/client/mixin/SkyRendererMixin.java`
- `mod-fabric/src/client/java/globe/world/client/mixin/ShaderManagerMixin.java`

## Related Docs

- [Client](client.md)
- [Local Solar Time](local-solar-time.md)
- [Vanilla time and sky](../vanilla-mechanics/time-and-sky.md)

## Open Audits

- Weather, lightning, night vision, and gamma should be manually checked with
  scrolling day/night enabled.
- Curvature horizon and cloud alignment need in-game tuning across sea level,
  mountains, tiny tiles, fog, and large render distances.
