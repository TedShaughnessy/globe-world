# Globe Object

## What

Globe World registers a `globe` block/item that can display an Overworld
canonical-tile texture. The object can be held, placed, broken, and picked from
the Functional Blocks creative tab.

## Why

The globe gives players a world-scale readout of the finite canonical tile.
The current implementation is biased toward projection testing: it fills the
small test tile automatically instead of relying on player exploration.

## Implementation

`GlobeWorldBlocks` registers the `globe` block, matching `BlockItem`, and a
minimal `GlobeBlockEntity` type. `GlobeBlock` supplies a sphere-like outline
and collision shape.

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
Both renderers fall back to the blank generated sphere until a snapshot is
available.

`GlobeSphereMesh` projects the canonical tile texture with a front-facing
curved projection. The projection maps angular distance from the front of the
sphere to radial distance in the tile texture, keeping the only unavoidable
projection singularity at the hidden back of the sphere instead of creating
latitude/longitude poles. Held globes continuously use the local client's
canonical player position as the projection center. Placed globes capture a
viewer-local projection center and facing when their render state first sees an
available map texture, so walking around a placed globe shows different sides of
the same local projection. Real exploration rules are still planned work in
[Globe Object Plan](../plans/globe-object.md).

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
- `mod-fabric/src/client/java/globe/world/client/render/GlobeSphereMesh.java`
- `mod-fabric/src/main/resources/assets/globe-world/blockstates/globe.json`
- `mod-fabric/src/main/resources/assets/globe-world/items/globe.json`
- `mod-fabric/src/main/resources/assets/globe-world/models/block/globe.json`
- `mod-fabric/src/main/resources/data/globe-world/loot_table/blocks/globe.json`
