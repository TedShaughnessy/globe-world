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

## Key Files

- `src/main/java/globe/world/mixin/MapItemMixin.java`
- `src/main/java/globe/world/mixin/MapItemSavedDataMixin.java`
- `src/main/java/globe/world/util/CoordUtil.java`

## Related Vanilla Mechanics

- `net.minecraft.world.item.MapItem`
- `net.minecraft.world.level.saveddata.maps.MapItemSavedData`
