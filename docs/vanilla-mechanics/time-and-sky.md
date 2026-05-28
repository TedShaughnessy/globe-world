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

For Globe World's intended realistic day/night gameplay, that global sky-darken
model is a blocker. Sleeping, mob spawning, mob burning, villager schedules, and
similar systems need separate audits so they can use local solar time at the
checked block/entity position instead of the dimension-wide value.
