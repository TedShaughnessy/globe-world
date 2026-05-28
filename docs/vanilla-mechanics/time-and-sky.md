# Time And Sky

Minecraft 26.1.2 drives most client sky and lightmap visuals through
environment attributes. The Overworld day timeline writes time-of-day values
into those attributes, the camera samples them at its current position, and the
sky/lightmap render-state extractors copy the sampled values into render state.

## Source Files

Client-only sources:

- `net/minecraft/client/multiplayer/ClientLevel.java`
- `net/minecraft/client/Camera.java`
- `net/minecraft/client/renderer/SkyRenderer.java`
- `net/minecraft/client/renderer/CloudRenderer.java`
- `net/minecraft/client/renderer/LightmapRenderStateExtractor.java`
- `net/minecraft/client/renderer/LevelRenderer.java`

Common/shared sources:

- `net/minecraft/world/attribute/EnvironmentAttributes.java`
- `net/minecraft/world/attribute/EnvironmentAttributeSystem.java`
- `net/minecraft/world/attribute/EnvironmentAttributeLayer.java`
- `net/minecraft/world/attribute/EnvironmentAttributeProbe.java`
- `net/minecraft/world/attribute/WeatherAttributes.java`
- `net/minecraft/world/timeline/Timelines.java`
- `net/minecraft/world/timeline/Timeline.java`
- `net/minecraft/world/timeline/AttributeTrackSampler.java`
- `net/minecraft/world/level/Level.java`

## Environment Attribute Pipeline

`EnvironmentAttributes.java:43` through `EnvironmentAttributes.java:85`
declares the visual sky/light attributes used by the client: sky color,
sunrise/sunset color, sun/moon/star angles, star brightness, sky light color,
sky light factor, and ambient light color.

`EnvironmentAttributes.java:105` through `EnvironmentAttributes.java:107`
declares `SKY_LIGHT_LEVEL` as a gameplay attribute, and
`EnvironmentAttributes.java:152` declares `MONSTERS_BURN`. These are examples
of global gameplay-facing day/night attributes that should stay out of a
visual-only local-solar-time MVP.

`EnvironmentAttributeSystem.java:55` through
`EnvironmentAttributeSystem.java:64` builds default layers in this order:
dimension constants, biome positional layers, dimension timelines, then weather
layers. That ordering matters if a mod wants to replace local time while still
letting rain and thunder modify the result.

`EnvironmentAttributeSystem.java:231` through
`EnvironmentAttributeSystem.java:249` applies positional sampling by running
constant, time-based, and positional layers in the order they were added.
`EnvironmentAttributeSystem.java:252` through
`EnvironmentAttributeSystem.java:264` shows that dimension-value queries ignore
positional layers.

`EnvironmentAttributeLayer.java:15` through
`EnvironmentAttributeLayer.java:23` is the key limitation: time-based layers get
only a cache tick id, while positional layers get the sampled position. Local
solar time therefore cannot be implemented as a simple clock offset inside a
vanilla time-based layer unless another layer or helper supplies position.

## Overworld Day Timeline

`Timelines.java:43` through `Timelines.java:155` registers the built-in
Overworld day timeline with a `24000` tick period.

Important tracks:

- `Timelines.java:53` through `Timelines.java:55`: sun, moon, and star angles.
- `Timelines.java:63` through `Timelines.java:80`: sky color, sky light color,
  sky light factor, and sky light level night modifiers.
- `Timelines.java:82` through `Timelines.java:116`: sunrise/sunset color.
- `Timelines.java:117` through `Timelines.java:132`: star brightness.
- `Timelines.java:154`: monster burning gameplay state.

`Timeline.java:147` through `Timeline.java:153` creates an
`AttributeTrackSampler` for an attribute. `AttributeTrackSampler.java:37`
through `AttributeTrackSampler.java:45` samples the configured world clock's
total ticks and applies the track modifier.

## Time Command And Clock Rate

`TimeCommand.java:91` through `TimeCommand.java:95` registers `/time rate`
with a positive float argument. `TimeCommand.java:195` through
`TimeCommand.java:197` implements the command by calling
`ServerClockManager.setRate(...)` for the selected clock.

`ServerClockManager.java:64` through `ServerClockManager.java:69` ticks all
world clocks when the global `ADVANCE_TIME` game rule is true.
`ServerClockManager.java:109` through `ServerClockManager.java:111` stores a
new per-clock rate, and `ServerClockManager.java:162` through
`ServerClockManager.java:168` advances each clock by accumulating that rate into
whole ticks. A rate below `1.0` lengthens the day; a rate above `1.0` shortens
it.

`ServerClockManager.java:113` through `ServerClockManager.java:122` broadcasts
clock changes to clients and invalidates level environment-attribute tick
caches. Mods should therefore apply clock-rate changes only after the server's
player list and levels exist.

`DimensionTypes.java:43` through `DimensionTypes.java:62` assigns the
Overworld dimension type the `WorldClocks.OVERWORLD` default clock. The Nether
has no default clock at `DimensionTypes.java:64` through `DimensionTypes.java:98`,
while the End uses `WorldClocks.THE_END` at `DimensionTypes.java:100` through
`DimensionTypes.java:129`.

## Client Sampling

`ClientLevel.java:249` builds the client level's environment attribute system.
`ClientLevel.java:253` through `ClientLevel.java:260` adds client flash layers
after the defaults. `ClientLevel.java:286` through `ClientLevel.java:339`
invalidates the environment attribute tick cache every client tick.

`Camera.java:86` through `Camera.java:90` ticks the camera's
`EnvironmentAttributeProbe` with the current camera position. `Camera.java:420`
through `Camera.java:421` exposes that probe to renderers.

`EnvironmentAttributeProbe.java:29` through
`EnvironmentAttributeProbe.java:39` records the level, position, and
biome-interpolated attributes. `EnvironmentAttributeProbe.java:41` through
`EnvironmentAttributeProbe.java:45` returns partial-tick interpolated attribute
values.

`LevelRenderer.java:571` through `LevelRenderer.java:595` extracts sky render
state from the camera. `LevelRenderer.java:607` through
`LevelRenderer.java:609` samples cloud color and height from the same camera
probe.

`CloudRenderer.java:137` through `CloudRenderer.java:168` derives cloud texture
position from camera X/Z plus a slow game-time X drift. X advances by
`gameTime * 0.03` blocks, while player movement contributes directly through
camera X/Z.

## Sky And Lightmap Consumers

`SkyRenderer.java:277` through `SkyRenderer.java:296` copies sampled
environment attributes into `SkyRenderState`: sun angle, moon angle, star angle,
star brightness, sunrise/sunset color, moon phase, and sky color.

`SkyRenderer.java:328` through `SkyRenderer.java:350` renders the sun, moon,
and stars from those extracted angles and brightness values.
`SkyRenderer.java:437` through `SkyRenderer.java:449` orients and colors the
sunrise/sunset fan.

`LightmapRenderStateExtractor.java:44` through
`LightmapRenderStateExtractor.java:83` samples `SKY_LIGHT_FACTOR`,
`SKY_LIGHT_COLOR`, `AMBIENT_LIGHT_COLOR`, block light tint, night vision,
darkness, gamma, and lightning flash state into the lightmap render state.

`WeatherAttributes.java:13` through `WeatherAttributes.java:33` defines rain
and thunder modifiers for sky color, sky light level, sky light color, sky light
factor, star brightness, and sunrise/sunset color.
`WeatherAttributes.java:37` through `WeatherAttributes.java:62` installs those
modifiers as time-based layers.

## Globe World Implications

For client visuals, a local solar time feature should not need to touch server
light engines or chunk light packets. The relevant vanilla surfaces are the
environment attribute layers and the client render-state extractors.

The cleanest hook is probably a client-only layer that runs after the vanilla
day timeline but before weather, so rain/thunder still darken the locally
computed sky. A mixin into `EnvironmentAttributeSystem.addDefaultLayers(...)`
near the `WeatherAttributes.addBuiltinLayers(...)` call is one possible
insertion point, gated to client Globe worlds.

Adding layers only at the end of `ClientLevel.addEnvironmentAttributeLayers(...)`
is easier, but the layer sees already-weathered values. That path either needs
careful multiplicative/lerp adjustments or accepts that some weather and
lightning behavior may be overwritten.

Because `Level.updateSkyBrightness()` uses
`environmentAttributes().getDimensionValue(EnvironmentAttributes.SKY_LIGHT_LEVEL)`,
positional local time will not change `Level.getSkyDarken()` or
`Level.isBrightOutside()`.

For Globe World's intended scrolling day/night gameplay, that global sky-darken
model is a blocker. Sleeping, mob spawning, mob burning, villager schedules, and
similar systems need separate audits so they can use local solar time at the
checked block/entity position instead of the dimension-wide value.

## Core Gameplay Predicates

Vanilla sleep eligibility flows through `BedRule.WHEN_DARK`, whose
`BedRule.Rule.test(...)` calls `Level.isDarkOutside()`. `ServerPlayer.java:1188`
through `ServerPlayer.java:1190` samples `EnvironmentAttributes.BED_RULE` at the
bed position and asks `BedRule.canSleep(...)`; `Player.java:245` through
`Player.java:246` rechecks the bed rule while a player remains asleep.
`ServerLevel.java:358` through `ServerLevel.java:365` checks the dimension's
`SleepStatus` and advances the default clock to
`ClockTimeMarkers.WAKE_UP_FROM_SLEEP` before waking every sleeping player.

Vanilla hostile spawning checks local block and sky light in
`Monster.isDarkEnoughToSpawn(...)`. `Monster.java:86` through `Monster.java:97`
first checks stored sky light and block light, then asks
`getMaxLocalRawBrightness(...)`; the no-argument call uses
`LevelReader.getSkyDarken()`, so it is global unless a mod supplies a
position-aware darkening value. Phantoms are a separate custom-spawner path:
`PhantomSpawner.java:32` gates the whole player loop on `ServerLevel.getSkyDarken()`
before `PhantomSpawner.java:35` through `PhantomSpawner.java:38` checks each
player's sky access and local difficulty.

Vanilla undead burning is in `Mob.isSunBurnTick(...)`. `Mob.java:505` through
`Mob.java:510` checks `EnvironmentAttributes.MONSTERS_BURN`, the entity's light
level dependent brightness, water/rain/powder-snow protection, and sky
visibility before `burnUndead(...)` applies helmet damage or fire.

Globe World hooks only these predicate surfaces for the first gameplay pass.
Stored sky light propagation, `Level.getSkyDarken()`, and the global clock
remain vanilla/global.

## Secondary Gameplay Consumers

`UpdateActivityFromSchedule.java:9` asks each scheduled brain to update from
the level environment attributes at the entity position. `Brain.java:332`
through `Brain.java:337` samples the brain's schedule attribute, which is
usually `VILLAGER_ACTIVITY` or `BABY_VILLAGER_ACTIVITY`, and changes activity
when the scheduled value differs from the current activity.

`Bee.java:340` through `Bee.java:345` makes bees want to enter hives when they
have nectar, are tired of looking for nectar, or the positional
`BEES_STAY_IN_HIVE` attribute is true.

`TurtleEggBlock.java:95` through `TurtleEggBlock.java:97` random-ticks eggs and
only advances hatching when `shouldUpdateHatchLevel(...)` passes.
`TurtleEggBlock.java:136` through `TurtleEggBlock.java:137` samples
`TURTLE_EGG_HATCH_CHANCE` at the egg position.

`PatrolSpawner.java:27` gates patrol attempts on dimension-wide
`ServerLevel.isBrightOutside()` before a player or spawn position is chosen.
`PatrolSpawner.java:39` later checks `CAN_PILLAGER_PATROL_SPAWN` at the chosen
spawn position. A local-daylight implementation has to open or replace the
first global gate, then apply the local daylight rule once a position exists.

Client clock item models use the numeric `Time` property. `Time.java:54`
through `Time.java:57` shows that the `"daytime"` source samples `SUN_ANGLE` at
the item owner position, so a positional local-sun-angle layer is enough for
clocks to display local time.
