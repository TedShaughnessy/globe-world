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
alias. The Minecraft 26.1.2 audit table lives in
[Packet Policies](packet-policies.md).

Server-authoritative settings are synchronized to modded clients during the
Fabric configuration phase before play starts. The wire payload uses
`GlobeSettings`, the same split model saved by the server. The client applies
those settings before first chunks, entity packets, and rendering decisions,
then acknowledges the configuration task so the join can continue. Runtime
server setting changes from commands or an integrated LAN host are sent again
during the play phase. If a world has wrapping enabled and a joining client
cannot receive the settings payload, the server disconnects that client with a
Globe World client-required message.

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
`GlobeSettings.presentation()`, exposed through world creation and
pause/options UI, and can be disabled with `0%`.
`/globeworld config set curvature <0-100>` and
`/globeworld config set nether_curvature <0-100>` update the saved curvature
settings at runtime.

Block, entity, and item POV picking follow the rendered curve through
`GlobeCurvedRaycast`. Client targeting and server item validation use the same
world/dimension curvature setting, so buckets, boats, fluids, signs, entities,
and block interactions resolve to the visually selected target while vanilla
reach, permissions, and final state checks remain authoritative. Entity physics
and collision boxes are not curved; only picking and presentation are.

Curvature also reaches selected-block outlines, block-breaking progress
overlays, item entities, third-person held items such as skeleton bows,
fox mouth-held items, thrown item projectiles such as Eyes of Ender, End portal
block-entity surfaces, boat water masks, clouds, and the sky horizon.
The sky disc, sun, moon, sunrise/sunset fan, and lower dark disc receive a
camera-relative horizon offset so the sky better matches curved terrain.
The shader helpers intentionally skip orthographic projections, keeping GUI
previews such as inventory players and special item models flat.

Globe World caps the effective client render distance for curved small-tile
worlds so the shader does not spend distance budget beyond the useful curved
horizon. Sodium uses that same distance as both its horizontal and vertical
cylindrical section-culling limit, which can hide ground sections when the
camera is high above terrain. For Sodium `0.8.12+mc26.1.2`, the optional
Sodium compatibility mixins keep Globe's capped X/Z distance for the horizon
illusion, but expand Sodium's vertical render-distance tests while curvature is
active. The mixins are version-gated because Sodium's internal renderer classes
are not a stable API.

## Settings UI

`GlobeWorldSettingsControls` backs both the create-world Globe World tab and the
in-world options screen. Its interactive controls include hover tooltips for
custom topology methods, Overworld and Nether curvature, day-length multiplier,
day/night behavior, natural-spawn exclusions, and forced progression-structure
toggles.
The controls mutate the split `GlobeSettings` sections directly: topology
controls update `TopologySettings`, curvature controls update
`PresentationSettings`, and day/night controls update `GameplaySettings`.
In remote multiplayer, the in-world screen shows the synced server settings as
read-only; local clients cannot silently edit only their own `GlobeConfig`.
The forced progression-structure toggles are editable only during world
creation because they describe world-generation policy. They remain visible but
disabled in the in-world options screen.
In simple create-world mode, changing the Overworld tile-size preset resets the
dependent settings below it to simple defaults, including a valid default Nether
tile size. Simple presets start at 8 chunks / 128 m so both Overworld and Nether
simple choices stay above the smallest realistically playable world size.
Presets larger than the Italy-size tile also default Overworld curvature to off
because the curve is no longer visually useful at that scale. When Distant
Horizons is loaded, its
Earth-curvature advice is shown only for tile sizes whose recommended DH
curvature ratio is within the supported `50..5000` range. Simple mode disables
scrolling day cycle below a 7,000-block Overworld tile and resets that setting
to Vanilla, because that tile is small enough for a running player to keep pace
with the sun. Simple mode does not expose extra natural-spawn controls: it keeps
the player mob-spawn exclusion at vanilla `24` blocks, and for Overworld tiles
of `16` chunks / `256` m or smaller it silently allows natural mobs inside the
saved world-spawn exclusion. Custom mode exposes both natural-spawn settings.
The create-world Nether controls split tile size from portal travel ratio.
In simple mode, Nether size can be disabled or chosen from relative presets such
as `1/8 size`, `Same size`, and `4x size`. The portal ratio is implied by the
selected Nether size: a `1/8 size` Nether uses vanilla-style `1:8` travel,
`Same size` uses `1:1`, and larger-than-Overworld Nether sizes use reverse
ratios. In custom mode,
Nether size is entered as a direct chunk count and portal ratio is a separate
preset slider from reverse `1:32` through `1:32`. A custom-mode `Tile Nether`
toggle disables the Nether inputs when Nether wrapping is off. Changing the custom
Overworld tile size refreshes the default Nether tile size; changing the portal
ratio does not alter Nether tile size or terrain mode.
In custom create-world mode, explicit Overworld and Nether topology methods are
available next to the corresponding size controls; changing the Overworld tile
size resets only the Overworld topology method, while changing the Nether tile
size resets the Nether topology method back to `Auto`. Tile sizes up to 256
chunks default forced progression structures on for the matching dimension;
larger effective tile sizes default them off.

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
Standalone remote players can also render one ring of client-only copies around
the camera for small tile worlds. The local camera player and mounted player
stacks are skipped.

The aliases use the same real client entity id and are culled by vanilla entity
view distance, the configured camera tile-ring limit for non-player entities,
the one-ring player limit, frustum checks, and compiled-section visibility.
Alias-aware picking tests shifted entity boxes but returns the canonical entity,
so interactions still target the real server entity. Tile-sized rebases from
server packets snap instead of interpolating across the tile, including
standalone remote-player rebases.

Locator-bar player waypoints use the receiver-nearest alias. Block and chunk
waypoint connections resend when the receiving player moves into a different
nearest visual tile; azimuth waypoint connections compute their angle through
the shortest wrapped path.

Fishing-line rendering is adjusted after vanilla extracts
`FishingHookRenderState`. `FishingHookRendererMixin` keeps the rendered hook in
the packet/visual-alias position, then moves the owner hand endpoint to the
nearest X/Z alias relative to that hook so the line does not stretch across a
whole tile. Fishing approach and bite particles are expected to use the existing
particle packet virtualization path; they remain a focused manual validation
case.

## Local Sky And Diagnostics

Scrolling day/night mode installs local environment-attribute layers for sky,
fog, and cloud color, sun/moon/star angles, star brightness, sunrise/sunset
color, and lightmap sky brightness. See
[Scrolling Day/Night](scrolling-day-night.md) and [Local Solar Time](local-solar-time.md).

Client diagnostics are intentionally targeted:

- `F3+Y`: Globe debug overlay and tile-border renderer, including current
  Overworld/Nether tile widths, Nether portal ratio, natural-spawn settings,
  and the saved world-spawn marker plus active exclusion radius. The marker uses
  the client level's respawn data, which vanilla updates from the server's
  default-spawn packet.
- `/globeworld debug list`: show server diagnostic channels and whether each
  channel is enabled for this session.
- `/globeworld debug enable <channel>` and `/globeworld debug disable <channel>`:
  turn one server diagnostic channel on or off.
- `/globeworld debug clear`: turn all server diagnostic channels off.
- `/globeworld client entity_aliases`: show local entity visual alias settings.
- `/globeworld client entity_aliases mode`: cycle local entity visual alias mode.
- `/globeworld client entity_aliases rings`: cycle local entity visual alias ring limit.

## Key Files

- Packet virtualization:
  `BlockPacketUtil`, `ChunkPacketUtil`, `WorldEventPacketUtil`,
  `EntityPacketUtil`, `WaypointPacketUtil`,
  `PacketVirtualizationPolicies`,
  `ClientboundLevelChunkWithLightMixin`, `ChunkMapBiomeResendMixin`,
  `PlayerListBroadcastMixin`, `ServerLevelWorldEventMixin`,
  `ServerPlayerInteractionPacketMixin`.
- Settings sync:
  `GlobeWorldNetworking`, `GlobeWorldSettingsPayload`,
  `GlobeWorldSettingsAckPayload`, `GlobeClientNetworking`,
  `GlobeClientSettings`, `GlobeWorldSettingsScreen`,
  `GlobeWorldSettingsControls`.
- Curvature and picking:
  `GlobeCurvature`, `GlobeCurvatureShader`, `GlobeCurvedRaycast`,
  `GlobeWorldSettingsControls`, `ShaderManagerMixin`, `LocalPlayerMixin`,
  `ItemMixin`, `OptionsMixin`, `FrustumMixin`, `CloudRendererMixin`,
  `SkyRendererMixin`, `ItemEntityRendererMixin`, `ItemInHandLayerMixin`,
  `FoxHeldItemLayerMixin`, `ThrownItemRendererMixin`,
  `TheEndPortalRendererMixin`.
- Iris and shader packs:
  `GlobeIrisShaderBridge`, `IrisProgramSourceMixin`,
  `globe-world.iris.mixins.json`, `shaderpacks/globe-world-curvature/`,
  `shaderpacks/makeup-ultra-fast-globe-world/`.
- Sodium compatibility:
  `GlobeSodiumMixinPlugin`, `SodiumOcclusionCullerMixin`,
  `SodiumTraversableTreeMixin`, `globe-world.sodium.mixins.json`.
- Visual entity aliases:
  `GlobeEntityAliasing`, `GlobeEntityAliasMode`, `GlobeVisualAliasUtil`,
  `LevelRendererMixin`, `ClientPacketListenerMixin`,
  `FishingHookRendererMixin`,
  `GlobeEntityAliasDiagnostics`.
- Diagnostics and settings:
  `DiagnosticsChannel`, `GlobeDiagnostics`,
  `GlobeClientDebugCommands`, `GlobeDebugHud`, `GlobeDebugState`,
  `GlobeTileBorderRenderer`, `KeyboardHandlerMixin`.

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
