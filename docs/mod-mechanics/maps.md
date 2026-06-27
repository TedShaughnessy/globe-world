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
facing, whether the hologram projection is enabled, and their reward-beacon
loadout. Empty-hand use opens the Atlas power UI. Sneak-use toggles the
hologram on or off. Map discovery is shared world state, not per projector
state, so multiple projectors in the same dimension show the same canonical
tile texture.

`GlobeMapSavedData` stores a fixed `512x512` tile texture, a discovered bitset,
and compact vanilla map colors for the Overworld. `GlobeMapTracker` scans
Overworld players on a short server cadence, canonicalizes their X/Z positions
with `CoordUtil.wrapBlock(...)`, and reveals nearby canonical pixels through
`GlobeMapSavedData.revealAround(...)`. This makes exploration in any alias tile
discover the same canonical map pixels.

Discovery is shared world state. It is not per-player and is not stored on
individual Atlas Projector block entities. The tracker skips spectator players,
reveals a generous 48 block radius, rate-limits repeated reveals by canonical
movement and time, and caps sampled pixels per reveal pass so small tiles and
multiplayer exploration do not do all map work in a single tick. After each
reveal pass, single-pixel holes surrounded by discovered map pixels are filled
in so tiny missed spots do not linger when players have explored around them.

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

When held in first person, Atlas Projector items keep the ordinary block-item
hand pose and render a separate translucent projection above the held projector.
`GlobeHeldMapRenderer` samples a player-centered window from the shared
projector texture, so the projection moves under the player instead of moving a
player icon across a fixed map. The window uses vanilla map spans: the smallest
span from `128`, `256`, `512`, `1024`, and `2048` blocks that can contain the
tile, capped at `2048` blocks. The held viewport texture is sampled in canonical
world axes instead of being resampled for player yaw; the projected surface then
rotates with the player so the top of the projection is always the direction the
player is facing, letting map pixels become diagonal on screen. After both hands
submit their held models, the projection renders from a mirrored first-person
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
percentage, discovered block area, tile size, and literal full completion.
Tiny tiles receive no points until full completion; small tiles require at least
half discovery; larger tiles can earn points from absolute explored area.

`GlobeAtlasPowerState` is saved Overworld state keyed by canonical Atlas block
position. Loaded Atlas block entities mirror their saved loadout into this
state, and removed Atlases drop their reservation. Each loadout spends points
on radius tier, selected effects, per-effect level II upgrades, and the
travel-network toggle. The power screen greys out upgrades whose candidate
loadout would exceed the shared budget, while still allowing players to turn
existing powers off. If saved loadouts exceed the current budget after a
settings or discovery-state change, the deterministic powered subset is chosen
by most recently edited Atlas first, then canonical block-position order.

The first reward effect set is speed, haste, and regeneration. Loaded powered
Atlases periodically apply their selected level I or level II effects to
non-spectator players inside the selected radius using wrapped X/Z distance, so
players across a canonical edge can still qualify. Unloaded Atlases keep
reserving budget through saved state, but they do not apply effects.

The client Atlas power screen draws a compact grey in-game panel without the
vanilla beacon payment slot, confirmation row, inventory, or hotbar. Effect
selection is icon-based, each effect has a neighboring level II toggle, radius
and travel are small symbol controls, and linked-travel destinations remain
listed in the lower panel when available.

Full discovery creates the Mastered Atlas state and unlocks linked Atlas
travel. A travel-enabled source Atlas can instantly send a player to another
loaded, powered, travel-enabled Atlas in the same Overworld when the player is
inside the source radius, the destination map pixel is discovered, and a safe
arrival space exists next to or above the destination. Travel has a `30` second
per-player cooldown. The first implementation does not yet include channeled
travel cancellation, Atlas Flight, active visual state, or ownership rules.

## Key Files

- `mod-fabric/src/main/java/globe/world/mixin/MapItemMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/MapItemSavedDataMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/LevelSetBlockBroadcastMixin.java`
- `mod-fabric/src/main/java/globe/world/util/CoordUtil.java`
- `mod-fabric/src/main/java/globe/world/block/GlobeBlock.java`
- `mod-fabric/src/main/java/globe/world/block/entity/GlobeBlockEntity.java`
- `mod-fabric/src/main/java/globe/world/atlas/GlobeAtlasEffect.java`
- `mod-fabric/src/main/java/globe/world/atlas/GlobeAtlasLoadout.java`
- `mod-fabric/src/main/java/globe/world/atlas/GlobeAtlasPowerState.java`
- `mod-fabric/src/main/java/globe/world/atlas/GlobeAtlasPowers.java`
- `mod-fabric/src/main/java/globe/world/atlas/GlobeDiscoveryRewards.java`
- `mod-fabric/src/main/java/globe/world/map/GlobeMapSavedData.java`
- `mod-fabric/src/main/java/globe/world/map/GlobeMapTracker.java`
- `mod-fabric/src/main/java/globe/world/network/GlobeAtlasScreenPayload.java`
- `mod-fabric/src/main/java/globe/world/network/GlobeAtlasTravelPayload.java`
- `mod-fabric/src/main/java/globe/world/network/GlobeAtlasUpdatePayload.java`
- `mod-fabric/src/main/java/globe/world/network/GlobeMapSnapshotPayload.java`
- `mod-fabric/src/client/java/globe/world/client/GlobeAtlasPowerScreen.java`
- `mod-fabric/src/client/java/globe/world/client/mixin/ItemInHandRendererMixin.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeBlockEntityRenderer.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeHeldMapRenderer.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeToroidMesh.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeMapTextureCache.java`

## Related Vanilla Mechanics

- `net.minecraft.world.item.MapItem`
- `net.minecraft.world.level.saveddata.maps.MapItemSavedData`
