# Globe Object

## What

Globe World registers a `globe` block/item that can display an Overworld
canonical-tile texture. The object can be held, placed, broken, and picked from
the Functional Blocks creative tab.

## Why

The globe gives players a world-scale readout of the finite canonical tile.
The current implementation is biased toward projection testing: it fills the
small test tile automatically instead of relying on player exploration.

The active plan treats the original sphere projection as a failed topological
fit and pivots the object toward a toroidal map where canonical X and Z are the
two independent loops. The visible renderer has started that pivot, while some
sphere-era projection scaffolding still remains to be removed.

## Implementation

`GlobeWorldBlocks` registers the `globe` block, matching `BlockItem`, and a
`GlobeBlockEntity` type. `GlobeBlock` currently behaves as a projector base:
its selection/collision shape is only the small base, while the toroidal map is
a non-colliding hologram.

`GlobeMapSavedData` stores one 512x512 Overworld texture for the current tile
size. It keeps a discovered bitset, vanilla packed map-color bytes, and a
raster-fill cursor. If the saved tile size or format version no longer matches
the active Overworld topology, a fresh state is created for the new topology.

`GlobeMapTracker` runs from the server tick event. For the current projection
test pass, every tick it fills the next batch of texture pixels by sampling
canonical Overworld columns with vanilla map colors. The sampler wraps any
sample that crosses the tile edge back into the canonical tile and uses normal
chunk lookup so a very small test tile can populate without walking every
region.

The server sends `GlobeMapSnapshotPayload` snapshots to clients on join and when
the map revision changes. The first implementation sends full 512x512 snapshots
rather than dirty patches.

On the client, `GlobeMapTextureCache` converts the packed map bytes into a
dynamic texture. Unknown pixels render as a dark unexplored color. Placed
globes render through `GlobeBlockEntityRenderer`; held and inventory globes
render through a custom special item renderer registered by `GlobeRenderers`.
Both renderers now submit `GlobeToroidMesh`. Held and inventory globes remain a
small torus preview. Placed globes render a simple textured projector base plus
a large walk-through torus hologram above the block. The placed projector now
draws only the outside surface, using a mostly visible hologram translucency.

The placed projector stores its wrap-axis test setting in the block entity and
syncs it to clients. Right-click swaps the wrap axis between canonical X around
the major ring and canonical Z around the major ring. When Globe debug visuals
are enabled with `F3+Y`, placed globes render the earlier 2x2 comparison grid
instead: front-left outside surface with X on the major ring, front-right inside
surface with X on the major ring, back-left outside surface with Z on the major
ring, and back-right inside surface with Z on the major ring.

`GlobeSphereMesh` is now only a transitional source of shared constants and the
blank fallback sprite. Real exploration rules and cleanup of the transitional
sphere renderer code are still planned work in [Globe Object Plan](../plans/globe-object.md).

## Key Files

- `mod-fabric/src/main/java/globe/world/GlobeWorldBlocks.java`
- `mod-fabric/src/main/java/globe/world/block/GlobeBlock.java`
- `mod-fabric/src/main/java/globe/world/block/entity/GlobeBlockEntity.java`
- `mod-fabric/src/main/java/globe/world/map/GlobeMapSavedData.java`
- `mod-fabric/src/main/java/globe/world/map/GlobeMapTracker.java`
- `mod-fabric/src/main/java/globe/world/network/GlobeMapSnapshotPayload.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeMapTextureCache.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeBlockEntityRenderer.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeSpecialRenderer.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeToroidMesh.java`
- `mod-fabric/src/client/java/globe/world/client/render/GlobeSphereMesh.java`
- `mod-fabric/src/main/resources/assets/globe-world/blockstates/globe.json`
- `mod-fabric/src/main/resources/assets/globe-world/items/globe.json`
- `mod-fabric/src/main/resources/assets/globe-world/models/block/globe.json`
- `mod-fabric/src/main/resources/data/globe-world/loot_table/blocks/globe.json`
