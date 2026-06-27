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

Placed projectors store only local presentation state: attachment face,
horizontal facing, and whether the hologram projection is enabled. Right-click
toggles the hologram on or off. Map discovery is shared world state, not per
projector state, so multiple projectors in the same dimension show the same
canonical tile texture.

`GlobeMapSavedData` stores a fixed `512x512` tile texture, a discovered bitset,
and compact vanilla map colors for the Overworld. `GlobeMapTracker` currently
uses `fillNextPixels(...)` to raster-fill the canonical tile as projection
testing scaffolding. The client receives full `GlobeMapSnapshotPayload`
snapshots on join and revision changes, then `GlobeMapTextureCache` uploads the
dynamic texture. Unknown pixels are rendered with a distinct client color.

The remaining unfinished behavior is player-driven discovery: pixels should be
revealed around canonical player positions instead of being filled globally in
the background.

## Key Files

- `mod-fabric/src/main/java/globe/world/mixin/MapItemMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/MapItemSavedDataMixin.java`
- `mod-fabric/src/main/java/globe/world/util/CoordUtil.java`
- `mod-fabric/src/main/java/globe/world/block/GlobeBlock.java`
- `mod-fabric/src/main/java/globe/world/block/entity/GlobeBlockEntity.java`
- `mod-fabric/src/main/java/globe/world/map/GlobeMapSavedData.java`
- `mod-fabric/src/main/java/globe/world/map/GlobeMapTracker.java`
- `mod-fabric/src/main/java/globe/world/network/GlobeMapSnapshotPayload.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeBlockEntityRenderer.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeToroidMesh.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeMapTextureCache.java`

## Related Vanilla Mechanics

- `net.minecraft.world.item.MapItem`
- `net.minecraft.world.level.saveddata.maps.MapItemSavedData`
