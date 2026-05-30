# Client

## What

The client sees raw alias chunks as normal chunks. It does not own canonical
world authority; the server sends ordinary-looking packets at the alias
coordinates the player is tracking.

Client visuals can add presentation-only effects, such as terrain curvature,
without changing canonical server state.

## Why

Minecraft's client chunk cache, renderer, and networking are position-keyed. The
least invasive way to render multiple aliases is to keep packets in vanilla
shape and change their coordinates before the client receives them.

## Implementation

Full chunk packets are sourced from canonical chunk data and relabeled to the
alias chunk position. Block, section, block-entity, incremental light, and
entity packets are copied or virtualized per viewer. World-event packets such as
sounds, particles, level events, block events, block destruction progress, and
explosion centers are also copied per viewer so their visible X/Z matches the
nearest loaded alias. Biome resend packets are copied to loaded alias chunk
positions. Entity-adjacent damage, vehicle, and minecart packets with absolute
positions are copied to the receiving viewer's nearest alias. Direct sign-editor
and look-at packets are also copied so the client opens or aims at the visible
alias target. Block/chunk locator-bar waypoint packets use wrapped connection
checks and alias positions; azimuth-only waypoints remain angle-based. Full
chunk light data and later incremental light updates both arrive at the alias
chunk coordinates the client has loaded.

Spawn, lodestone, and recovery compass needles resolve their target block
through the nearest virtual alias in tiled dimensions. They point across the
shortest wrapped X/Z path to world spawn, the bound lodestone, or the last death
location instead of always aiming at the raw stored `GlobalPos`.

The curvature visual pass rewrites relevant world vertex shaders at resource
load time. Overworld and Nether curvature are saved separately in
`TilingSettings`, exposed through the world creation and pause/options UI, and
can be disabled with `0%`.
Cloud vertices use the same shader curvature transform as terrain so vanilla's
flat cloud layer bends with the world presentation.
For small tiles, cloud texture sampling scales the camera X/Z contribution so
player movement produces stronger cloud parallax. The vanilla time drift and
cloud height are unchanged.
The sky renderer applies a matching camera-relative horizon offset to the sky
disc, lower dark disc, sunrise/sunset fan, sun, and moon. The offset is derived
from the terrain curvature radius and the camera's height above the dimension
horizon, so the sky horizon moves down as the curved terrain horizon falls
away. Sun and moon quads are rendered on a larger effective sky sphere and
scaled up with it, which keeps noon and midnight high while still letting
sunrise, sunset, moonrise, and moonset track the lowered visual horizon. Stars
keep their vanilla/local-time dome.

Local solar-time day/night is implemented for client visuals under
`DayNightCycleMode.SCROLLING`. The world-creation and pause/options UI can
select it and set a saved day-length multiplier. `CoordUtil` exposes shared
longitude-based local solar time helpers, and the client replaces the Overworld
visual day timeline with camera-position-aware environment attribute layers.
Those layers are installed for Globe worlds and choose vanilla or local sampling
from the current day-cycle mode, so pause/options mode changes take effect
without reloading the save.
Sky color, sun/moon/star angles, star brightness, sunrise/sunset color, and
lightmap sky brightness use local canonical X while weather layers still run
afterward. The day-length multiplier changes the underlying Overworld clock
rate, so it affects both vanilla and scrolling day/night modes. The core server
gameplay predicates for sleeping, monster spawning brightness, and undead
burning also use local solar time. Other global time predicates are documented
as scrolling day/night boundaries.

Client diagnostics remain targeted at alias loading, tracking, tile borders,
and settings state. The client has a dedicated Globe World debug overlay toggled
with `F3+Y`; it draws separate wrapped/canonical and absolute/alias columns
without adding Globe World lines to vanilla F3. The wrapped/canonical column
shows local solar time plus the active day-cycle mode and day-length multiplier.
Tile-border rendering remains available with `F3+Shift+Y`.

## Key Files

- `src/main/java/globe/world/mixin/ClientboundLevelChunkWithLightMixin.java`
- `src/main/java/globe/world/mixin/ClientboundLightUpdatePacketAccessor.java`
- `src/main/java/globe/world/util/BlockPacketUtil.java`
- `src/main/java/globe/world/util/ChunkPacketUtil.java`
- `src/main/java/globe/world/util/WorldEventPacketUtil.java`
- `src/main/java/globe/world/util/EntityPacketUtil.java`
- `src/main/java/globe/world/mixin/ClientboundPlayerLookAtPacketAccessor.java`
- `src/main/java/globe/world/mixin/ChunkMapBiomeResendMixin.java`
- `src/main/java/globe/world/mixin/PlayerListBroadcastMixin.java`
- `src/main/java/globe/world/mixin/ServerLevelWorldEventMixin.java`
- `src/main/java/globe/world/mixin/ServerPlayerInteractionPacketMixin.java`
- `src/main/java/globe/world/mixin/LivingEntityWaypointMixin.java`
- `src/main/java/globe/world/mixin/WaypointBlockConnectionMixin.java`
- `src/main/java/globe/world/mixin/WaypointChunkConnectionMixin.java`
- `src/client/java/globe/world/client/mixin/CompassAngleStateMixin.java`
- `src/client/java/globe/world/client/GlobeCurvatureShader.java`
- `src/client/java/globe/world/client/GlobeSkyHorizon.java`
- `src/client/java/globe/world/client/GlobeCurvatureSlider.java`
- `src/client/java/globe/world/client/GlobeWorldSettingsControls.java`
- `src/client/java/globe/world/client/GlobeWorldSettingsScreen.java`
- `src/client/java/globe/world/client/GlobeClientTilingSettings.java`
- `src/client/java/globe/world/client/mixin/CloudRendererMixin.java`
- `src/main/java/globe/world/util/CoordUtil.java`
- `src/main/java/globe/world/util/GlobeDayLength.java`
- `src/main/java/globe/world/config/DayNightCycleMode.java`
- `src/client/java/globe/world/client/GlobeScrollingSky.java`
- `src/client/java/globe/world/client/mixin/EnvironmentAttributeSystemBuilderMixin.java`
- `src/client/java/globe/world/client/GlobeTileBorderRenderer.java`
- `src/client/java/globe/world/client/GlobeDebugHud.java`
- `src/client/java/globe/world/client/GlobeDebugState.java`
- `src/client/java/globe/world/client/mixin/GuiMixin.java`
- `src/client/java/globe/world/client/mixin/KeyboardHandlerMixin.java`
- `src/client/java/globe/world/client/mixin/ShaderManagerMixin.java`
- `src/client/java/globe/world/client/mixin/SkyRendererMixin.java`
- `src/client/java/globe/world/client/mixin/CreateWorldScreenMixin.java`
- `src/client/java/globe/world/client/mixin/OptionsScreenMixin.java`
- `src/client/java/globe/world/client/mixin/WorldOptionsScreenMixin.java`
- `src/client/java/globe/world/client/mixin/SectionOcclusionGraphMixin.java`
- `src/client/java/globe/world/client/mixin/FrustumMixin.java`

## Related Docs

- [Client Canonical Chunk Cache](../plans/client-canonical-chunk-cache.md)
- [Scrolling Day/Night](scrolling-day-night.md)
- [Local Solar Time](local-solar-time.md)

## Related Vanilla Mechanics

- [Vanilla client world](../vanilla-mechanics/client-world.md)
- [Vanilla lighting](../vanilla-mechanics/lighting.md)
- [Vanilla time and sky](../vanilla-mechanics/time-and-sky.md)

## Open Audits

- Client-side canonical chunk cache is still planned, not authoritative.
- Curvature horizon alignment now has a first-pass sky offset and curved cloud
  shader. It still needs in-game tuning across sea level, mountains, tiny
  tiles, fog, and large render distances.
- Scrolling day/night weather interaction still needs manual verification.
