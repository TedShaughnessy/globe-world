# Client

## What

The client renders aliases as ordinary Minecraft chunks and entities. Globe
World keeps the client cache vanilla-shaped: each visible alias has its own raw
client coordinate, while canonical ownership stays on the server.

Client-only presentation layers add curvature, local sky behavior, visual entity
aliases, and diagnostics. Interaction packets still target canonical server
state.

## Why

Minecraft's client world, renderer, packet handlers, and chunk cache are keyed
by raw position. Relabeling server packets lets multiple aliases coexist without
teaching the client cache about canonical chunks.

## Packet And Cache Model

Full chunk packets are sourced from canonical chunks and relabeled to the alias
chunk position before the client sees them. Later block, section, block-entity,
biome, light, world-event, waypoint, sign-editor, look-at, map, and entity
packets are copied or virtualized per viewer so their X/Z matches the visible
alias.

`ClientboundPlayerPositionPacket` is not broadly virtualized because vanilla
uses it for teleport acknowledgement state. Globe World instead canonicalizes
players at login, respawn, and bed wake-up while leaving normal in-session
movement in the player's current coordinate frame.

The retired client canonical chunk cache proposal is intentionally not part of
the implementation. Alias warm-starting, cache dedupe, and client-side canonical
ownership remain out of scope; the server packet stream is the authority.

## Curvature And Picking

`GlobeCurvatureShader` rewrites relevant vanilla world vertex shaders at
resource load time. Overworld and Nether curvature are saved in
`TilingSettings`, exposed through world creation and pause/options UI, and can
be disabled with `0%`. `/globeworld config set curvature <0-100>` and
`/globeworld config set nether_curvature <0-100>` update the saved curvature
settings at runtime.

Block, entity, and item POV picking follow the rendered curve through
`GlobeCurvedRaycast`. Client targeting and server item validation use the same
world/dimension curvature setting, so buckets, boats, fluids, signs, entities,
and block interactions resolve to the visually selected target while vanilla
reach, permissions, and final state checks remain authoritative. Entity physics
and collision boxes are not curved; only picking and presentation are.

Curvature also reaches selected-block outlines, item entities, boat water masks,
clouds, and the sky horizon. The sky disc, sun, moon, sunrise/sunset fan, and
lower dark disc receive a camera-relative horizon offset so the sky better
matches curved terrain.

## Settings UI

`GlobeWorldSettingsControls` backs both the create-world Globe World tab and the
in-world options screen. Its interactive controls include hover tooltips for
Overworld and Nether curvature, day-length multiplier, and day/night behavior.
In simple create-world mode, changing the tile-size preset resets the dependent
settings below it to simple defaults. Presets larger than the Italy-size tile
also default Overworld curvature to off because the curve is no longer visually
useful at that scale. When Distant Horizons is loaded, its Earth-curvature
advice is shown only for tile sizes whose recommended DH curvature ratio is
within the supported `50..5000` range.

## Shader Packs

Sodium/Iris shader packs bypass vanilla shader rewriting, so Globe World ships
an Iris bridge and two shader-pack assets:

- `shaderpacks/globe-world-curvature/`: minimal reference pack that applies
  Globe curvature and fog behavior.
- `shaderpacks/makeup-ultra-fast-globe-world/`: pinned MakeUp Ultra Fast
  upstream metadata plus a small Globe curvature patch stack.

`IrisProgramSourceMixin` bakes Globe World's current curvature constants into
shader sources containing the documented placeholders. `GlobeIrisShaderBridge`
asks Iris to reload shaders when Globe curvature settings change. The detailed
shader-pack contract lives in
[Iris Shader Packs](../mod-compatibility/iris-shader-packs.md).

## Visual Entity Aliases

Non-player, not-leashed entities can render extra client-only copies at nearby
whole-tile offsets. Non-player mounted stacks share the root vehicle's offsets
so passengers and vehicles stay together in every visual copy.

The aliases use the same real client entity id and are culled by vanilla entity
view distance, the configured camera tile-ring limit, frustum checks, and
compiled-section visibility. Alias-aware picking tests shifted entity boxes but
returns the canonical entity, so interactions still target the real server
entity. Tile-sized rebases from server packets snap instead of interpolating
across the tile.

## Local Sky And Diagnostics

Scrolling day/night mode installs local environment-attribute layers for sky
color, sun/moon/star angles, star brightness, sunrise/sunset color, and lightmap
sky brightness. See [Scrolling Day/Night](scrolling-day-night.md) and
[Local Solar Time](local-solar-time.md).

Client diagnostics are intentionally targeted:

- `F3+Y`: Globe debug overlay and tile-border renderer.
- `/globeworld client entity_aliases`: show local entity visual alias settings.
- `/globeworld client entity_aliases mode`: cycle local entity visual alias mode.
- `/globeworld client entity_aliases rings`: cycle local entity visual alias ring limit.

## Key Files

- Packet virtualization:
  `BlockPacketUtil`, `ChunkPacketUtil`, `WorldEventPacketUtil`,
  `EntityPacketUtil`, `WaypointPacketUtil`,
  `ClientboundLevelChunkWithLightMixin`, `ChunkMapBiomeResendMixin`,
  `PlayerListBroadcastMixin`, `ServerLevelWorldEventMixin`,
  `ServerPlayerInteractionPacketMixin`.
- Curvature and picking:
  `GlobeCurvature`, `GlobeCurvatureShader`, `GlobeCurvedRaycast`,
  `GlobeWorldSettingsControls`, `ShaderManagerMixin`, `LocalPlayerMixin`,
  `ItemMixin`, `OptionsMixin`, `FrustumMixin`, `CloudRendererMixin`,
  `SkyRendererMixin`, `ItemEntityRendererMixin`.
- Iris and shader packs:
  `GlobeIrisShaderBridge`, `IrisProgramSourceMixin`,
  `globe-world.iris.mixins.json`, `shaderpacks/globe-world-curvature/`,
  `shaderpacks/makeup-ultra-fast-globe-world/`.
- Visual entity aliases:
  `GlobeEntityAliasing`, `GlobeEntityAliasMode`, `GlobeVisualAliasUtil`,
  `LevelRendererMixin`, `ClientPacketListenerMixin`,
  `GlobeEntityAliasDiagnostics`.
- Diagnostics and settings:
  `GlobeClientDebugCommands`, `GlobeDebugHud`, `GlobeDebugState`,
  `GlobeTileBorderRenderer`, `KeyboardHandlerMixin`, `GlobeClientTilingSettings`,
  `GlobeWorldSettingsScreen`.

## Related Vanilla Mechanics

- [Vanilla client world](../vanilla-mechanics/client-world.md)
- [Position-bearing packets](../vanilla-mechanics/position-bearing-packets.md)
- [Vanilla lighting](../vanilla-mechanics/lighting.md)
- [Vanilla time and sky](../vanilla-mechanics/time-and-sky.md)

## Open Audits

- Curvature horizon and cloud alignment need in-game tuning across sea level,
  mountains, tiny tiles, fog, and large render distances.
- Scrolling day/night weather, lightning, night vision, and gamma interaction
  still need manual verification.
