# Maps

## What

Filled maps remain fixed coordinate objects, but player positions are evaluated
through tiled aliases. In tiled dimensions, the player icon uses the alias of the
player's canonical X/Z that is closest to the map center.

## Why

Vanilla maps compute icons from absolute `player - mapCenter` coordinates. In a
tiled world, that can make a player disappear from a map even though an adjacent
alias of that same player belongs on the map. Center-relative aliasing keeps all
copies of the same map deterministic: every holder sees the same marker
position, and the marker lines up with the map pixels under it.

## Implementation

Map pixel refresh in `MapItem.update(...)` uses the held map center as the
viewer coordinate for player X/Z. The player's raw position is first
canonicalized, then moved to the virtual tile nearest `centerX`/`centerZ`.

Player decorations in `MapItemSavedData.tickCarriedBy(...)` use the same
center-relative alias rule before vanilla calculates whether the icon is on-map,
off-map, or off-limits. This applies to tracked player icons only; banners,
frames, and static exploration markers keep their stored map coordinates.

`ClientboundMapItemDataPacket` is therefore covered upstream: map-local pixels
and tracked-player decorations are corrected before vanilla builds the packet,
instead of being rewritten during send.

## Atlas Projectors

Atlas Projectors are placeable toroidal views of the canonical Overworld tile.
The original registry id is still `globe`, but the player-facing block family is
Atlas Projector, Copper Atlas Projector, and Soul Atlas Projector.

Placed projectors store local presentation state, attachment face, horizontal
facing, optional custom Atlas name, whether the hologram projection is enabled,
and their reward-beacon loadout. Empty-hand use opens the Atlas power UI, where
the name field defaults to the projector's canonical coordinates when no custom
name is saved. Sneak-use still toggles the hologram on or off. Map discovery is
shared world state, not per projector state, so multiple projectors in the same
dimension show the same canonical tile texture.

`GlobeMapSavedData` stores a fixed `512x512` tile texture, a discovered bitset,
and compact vanilla map colors for the Overworld. Full snapshots stay at this
size because clientbound custom payloads are capped at `1,048,576` bytes, and a
larger whole-tile texture would exceed that once discovery data is included. For
tiles up to `512` chunks wide, `GlobeMapTracker` scans Overworld players on a
short server cadence, canonicalizes their X/Z positions with
`CoordUtil.wrapBlock(...)`, and reveals nearby canonical pixels through
`GlobeMapSavedData.revealAround(...)`. This makes exploration in any alias tile
discover the same canonical map pixels.

Discovery is shared world state. It is not per-player and is not stored on
individual Atlas Projector block entities. The tracker skips spectator players,
reveals a generous 48 block radius, rate-limits repeated reveals by canonical
movement and time, and caps sampled pixels per reveal pass so small tiles and
multiplayer exploration do not do all map work in a single tick. After each
reveal pass, undiscovered components whose pixel span fits inside the reveal
footprint are sampled and filled, so small missed islands do not linger when
players have explored around them. Once the discovered bitset reaches `99%`,
the Atlas treats the tile as complete and the next reveal cleanup samples all
remaining hidden pixels so the rendered map catches up to the mastered state.

Discovered colors also refresh after block edits. `LevelSetBlockBroadcastMixin`
already observes successful canonical `Level.setBlock(...)` mutations; when a
changed Overworld column belongs to a discovered map pixel, it asks
`GlobeMapTracker.refreshChangedColumn(...)` to resample that pixel through
`GlobeMapSavedData.refreshColumn(...)`. Undiscovered pixels stay hidden, so
editing terrain in unexplored space does not reveal it.

The client receives full `GlobeMapSnapshotPayload` snapshots on join and
revision changes, then `GlobeMapTextureCache` uploads the dynamic texture.
Undiscovered pixels are uploaded as fully transparent pixels, so hidden regions
leave holes in the toroidal projection until players reveal them.

For tiles above `512` chunks wide, the Atlas switches to survey mode instead of
presenting the `512x512` texture as a literal whole-world map. `GlobeAtlasSurveyState`
stores shared visited biome ids, unique canonical chunks reached by
non-spectator players, per-visited-chunk representative biome ids, and whether
vanilla biome completion has been imported. Older saves that only have visited
chunk positions keep those chunks as discovered survey cells with unknown biome
color until a player revisits them. The tracker imports completed criteria from
vanilla's `minecraft:adventure/adventuring_time` advancement when present, then
records a small disk of already-loaded canonical chunks around each player.
Biome ids are sampled from loaded chunks only, so large-tile survey discovery
does not force new world generation. After each survey reveal, a bounded local
gap-fill pass marks small enclosed holes near the player, using biome ids when
the chunk is loaded and an unknown-biome fill otherwise.

Survey projection uses `GlobeAtlasSurveyWindowPayload`, not the whole-map
`GlobeMapSnapshotPayload`. Payloads carry centered chunk windows with a
discovered bitset, a biome-id palette, per-cell palette indexes, and simple
player/Atlas markers. Window construction uses saved survey entries while that
is cheaper than the requested window, then switches to fixed-size window-cell
lookups for heavily explored saves; it does not force-load or generate chunks
for display. Held survey windows are `128x128` chunks centered on the current
player's canonical chunk. Placed survey windows are `512x512` chunks centered on
loaded, projection-enabled Atlas Projectors near the receiving player, capped
per sync tick and rate-limited by center chunk and survey revision.

The client uploads survey windows through `GlobeAtlasSurveyTextureCache`, which
is separate from the literal map texture cache. Undiscovered chunks render as a
faint hologram fill, discovered chunks without biome ids render grey-blue, and
known-biome chunks use a stable deterministic biome color palette. Grid lines
and Atlas markers are baked into the dynamic survey texture. Held survey
windows omit a player marker because the player is already fixed at the center
of the projection. The held survey cache keeps the last rounded projection
texture while a newly centered held survey payload is still in flight, so
first-person rendering does not blink off on chunk-center changes. Held survey
copies are rotated 180 degrees from the placed survey texture so forward motion
scrolls the projected terrain down toward the player, matching the literal held
viewport.

When held in first person, Atlas Projector items keep the ordinary block-item
hand pose and render a separate translucent projection above the held projector.
`GlobeHeldMapRenderer` samples a player-centered window from the shared
projector texture, so the projection moves under the player instead of moving a
player icon across a fixed map. The held literal-map window uses vanilla-style
spans from `128`, `256`, `512`, `1024`, and `2048` blocks and bakes in the
same holographic grid language as survey windows, with an adaptive chunk grid
and a brighter center cross. In survey mode, the held renderer uses a rounded,
softly faded copy of the current `128x128` chunk player-centered survey-window
texture instead of resampling the literal Atlas texture.

On literal-map tiles, the placed Atlas hologram uses the same large-tile scale,
so the torus grows
with the held window instead of staying at the small-world size. The hologram
center rises with the scaled minor radius so large projections stay above the
projector block, and block-entity render culling is expanded for large
projections. In survey mode, placed Atlases render a quieter shallow square
domed holographic survey surface centered on that Atlas' canonical chunk
instead of the torus, with the corners participating in the curvature.
Projection-enabled placed Atlases also render a short, translucent,
variant-tinted light fan from the projector top toward the hologram surface, so
iron, copper, and soul projectors have a visible active state before the map
texture detail is readable. The fan tint is separate from the hologram tint:
iron uses a slightly warm lantern-like light, copper uses green light, and soul
keeps its cyan light. Survey-mode large-tile Atlases use the same fan at half
height so it stays tucked under the flatter placed survey surface.

The held viewport texture is sampled in canonical world axes
instead of being resampled for player yaw; the projected surface then rotates
with the player so the top of the projection is always the direction the player
is facing, letting map pixels become diagonal on screen. After both hands submit
their held models, the projection renders from a mirrored first-person
screen-space pose above the active hand, so left and right hands use symmetric
placement and the hologram appears in front of the item model. The window is
circular with a soft alpha fade at the edge, tilts toward the camera, and bows
slightly forward at its center. The held projection has no vanilla map-paper
backing. It uses a held-only dynamic texture where unknown space has a faint
hologram fill so the player-centered window remains visible, while placed
projector holograms still use fully transparent unknown pixels. Held projection
vertices render full-bright through a depth-free first-person textured pass, so
the map reads like a projector hologram rather than a hand-lit item surface. If
no current-dimension snapshot has arrived yet, the held projection renders
nothing.

## Exploration Rewards

Atlas Projectors can spend shared Overworld discovery progress on a local
reward-beacon loadout. `GlobeDiscoveryRewards` derives world effect points and a
per-Atlas radius cap from `GlobeMapSavedData` discovered pixels, discovered
percentage, discovered block area, tile size, and completion on tiles up to the
large-survey cutoff. Discovery at or above `99%` rounds up to `100%` for
completion rewards and Mastered Atlas checks.
Tiny tiles receive no points until full completion; small tiles require at least
half discovery; larger tiles can earn points from absolute explored area.

In survey mode, `GlobeDiscoveryRewards` derives points and radius caps from
visited biome count and visited canonical chunks. Linked Atlas travel unlocks
when the shared survey has visited `90%` of the canonical tile's chunks, capped
at `12800` chunks for very large tiles.
Chunks award one point per `64` unique chunks, while biomes award one point per
ten visited biomes with an extra two-point bonus when vanilla Adventuring Time
biome completion is imported. The travel-network toggle can be saved before
that unlock on large tiles, so players can prepare destinations while still
building survey progress. The Mastered Atlas advancement remains full-map
completion behavior for tiles where literal projection is enabled.

`GlobeAtlasPowerState` is saved Overworld state keyed by canonical Atlas block
position. Loaded Atlas block entities mirror their saved loadout and custom
name into this state, and removed Atlases drop their reservation. Each loadout
spends points on radius tier, selected effects, per-effect level II upgrades,
and the travel-network toggle. The projection toggle is local presentation
state and is free on literal-map and survey-mode tiles. The power screen greys
out upgrades whose candidate loadout would exceed the shared budget, while
still allowing players to turn existing powers off. If saved loadouts exceed
the current budget after a
settings or discovery-state change, the deterministic powered subset is chosen
by most recently edited Atlas first, then canonical block-position order.

The first reward effect set is speed, haste, and jump boost. Loaded powered
Atlases periodically apply their selected level I or level II effects to
non-spectator players inside the selected radius using wrapped X/Z distance, so
players across a canonical edge can still qualify. Unloaded Atlases keep
reserving budget through saved state, but they do not apply effects.

The client Atlas power screen draws a compact grey in-game panel without the
vanilla beacon payment slot, confirmation row, inventory, or hotbar. Effect
selection is icon-based, each effect has a neighboring level II toggle, radius
uses matching `R`, `II`, and `III` toggle buttons, and the projection and travel
buttons use the same symbol-control style. Discovery is shown as a progress bar
with point/radius milestone markers derived from `GlobeDiscoveryRewards`;
budget, radius cap, and current usage are shown alongside it on literal-map
tiles. The projection button remains available in survey mode and controls the
biome survey projection. The survey status area shows chunk progress
toward the travel target, visited biomes, point budget, and current usage.
The destinations tab lists saved Atlases by name and canonical location even
when travel is unavailable, using a scrollable list without the status area
shown on the Powers tab. Destination rows are enabled only when this Atlas and
the destination are loaded, powered, travel-enabled, and the current progression
mode has unlocked travel; disabled rows explain the blocking condition in their
tooltip.

Full discovery creates the Mastered Atlas state and unlocks linked Atlas
travel on literal-map tiles, and the shared completion state awards the
Mastered Atlas advancement to non-spectator Overworld players. On survey-mode
tiles, linked travel is unlocked by survey milestones instead. A travel-enabled
source Atlas can instantly send a player to another loaded, powered,
travel-enabled Atlas in the same Overworld when the player is inside the source
radius, the destination is valid for the current progression mode, and a safe
arrival space exists above, below, inside the same block as, or next to the
destination projector. Literal-map tiles still require the destination map
pixel to be discovered; survey-mode tiles rely on the powered survey network.
Arrival selection uses the
player's collision box, so wall-mounted and ceiling-mounted projectors can be
valid destinations when the thin projector shape leaves room for the player.
Wall-mounted destinations prefer the vertical column directly in the projector's
facing direction, so projectors embedded flush in a wall can place the player
next to the wall with the Atlas at foot or head height. Destination buttons use
custom Atlas names when present, otherwise canonical coordinates. Clicking a
destination sends a one-shot travel request and closes the Atlas screen; denied
or failed requests leave the screen closed and show feedback. Travel has no
recharge delay, so players can use another linked Atlas immediately after
arriving. Channeled travel, Atlas Flight, active visual state, and ownership
rules are not part of the current reward scope.

## Key Files

- `mod-fabric/src/main/java/globe/world/mixin/MapItemMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/MapItemSavedDataMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/LevelSetBlockBroadcastMixin.java`
- `mod-fabric/src/main/java/globe/world/util/CoordUtil.java`
- `mod-fabric/src/main/java/globe/world/GlobeWorldBlocks.java`
- `mod-fabric/src/main/java/globe/world/block/GlobeBlock.java`
- `mod-fabric/src/main/java/globe/world/block/entity/GlobeBlockEntity.java`
- `mod-fabric/src/main/java/globe/world/atlas/GlobeAtlasEffect.java`
- `mod-fabric/src/main/java/globe/world/atlas/GlobeAtlasLoadout.java`
- `mod-fabric/src/main/java/globe/world/atlas/GlobeAtlasPowerState.java`
- `mod-fabric/src/main/java/globe/world/atlas/GlobeAtlasPowers.java`
- `mod-fabric/src/main/java/globe/world/atlas/GlobeAtlasSurvey.java`
- `mod-fabric/src/main/java/globe/world/atlas/GlobeAtlasSurveyState.java`
- `mod-fabric/src/main/java/globe/world/atlas/GlobeAtlasSurveyWindows.java`
- `mod-fabric/src/main/java/globe/world/atlas/GlobeDiscoveryRewards.java`
- `mod-fabric/src/main/java/globe/world/map/GlobeMapSavedData.java`
- `mod-fabric/src/main/java/globe/world/map/GlobeMapTracker.java`
- `mod-fabric/src/main/resources/data/globe-world/advancement/mastered_atlas.json`
- `mod-fabric/src/main/java/globe/world/network/GlobeAtlasScreenPayload.java`
- `mod-fabric/src/main/java/globe/world/network/GlobeAtlasTravelPayload.java`
- `mod-fabric/src/main/java/globe/world/network/GlobeAtlasUpdatePayload.java`
- `mod-fabric/src/main/java/globe/world/network/GlobeAtlasSurveyWindowPayload.java`
- `mod-fabric/src/main/java/globe/world/network/GlobeMapSnapshotPayload.java`
- `mod-fabric/src/client/java/globe/world/client/GlobeAtlasPowerScreen.java`
- `mod-fabric/src/client/java/globe/world/client/mixin/ItemInHandRendererMixin.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeBlockEntityRenderer.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeHeldMapRenderer.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeAtlasSurveyTextureCache.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeToroidMesh.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeMapTextureCache.java`

## Related Vanilla Mechanics

- `net.minecraft.world.item.MapItem`
- `net.minecraft.world.level.saveddata.maps.MapItemSavedData`
