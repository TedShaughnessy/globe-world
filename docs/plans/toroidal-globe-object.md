# Toroidal Globe Object Plan

This plan now treats the sphere-based globe object as a failed projection
experiment. The useful object for Globe World's wrapped rectangular tile is a
toroid: the canonical tile wraps independently in X and Z, and a torus is the
surface with exactly those two independent loops.

The original block/item keeps the internal `globe` id for compatibility, but
the player-facing object family is `Atlas Projector`. Its durable renderer
target is a toroidal world map rather than a miniature sphere.

## Why The Sphere Plan Failed

The current sphere prototype was trying to make the tile look like Globe World's
curved terrain illusion. That was attractive visually, but it fights the actual
topology of the world:

- The canonical tile is a rectangular periodic domain. X wraps to X and Z wraps
  to Z, so the real map surface is a torus.
- A sphere cannot represent both tile cycles without inventing poles,
  singularities, or hidden compression.
- The six-chart placed-globe pass reduced the worst artifacts, but it also made
  the object viewer-relative. That is not a stable map of the world.
- Hiding distortion on the back of a sphere makes the object prettier, not more
  useful. Players need to understand both seams and the continuous tile layout.

The conclusion: keep the saved map state and sync work, but replace the sphere
projection renderer with a toroidal representation.

## Intended Behavior

- Players can craft or obtain an Atlas Projector item and place it as a block.
- A placed object shows explored parts of the current world's canonical tile on
  a toroidal surface.
- One torus loop corresponds to one full wrap in canonical X; the other
  corresponds to one full wrap in canonical Z.
- The placed projector can swap which canonical axis occupies the torus major
  ring while testing the most readable orientation.
- Exploration progresses automatically while players move through the world,
  instead of requiring a held map update loop.
- The texture is server-owned world state. The torus mesh and any local
  highlights are client rendering details.
- Multiple placed objects in the same dimension normally show the same
  discovered tile map.

This makes the object a readable model of the mod's actual world: walking across
the X seam moves around the torus's main ring; walking across the Z seam moves
around the tube.

## Design Shape

Treat the object as three separate systems:

1. A server-owned exploration state for the canonical tile.
2. A placeable block and block entity that exposes that state to clients.
3. A client renderer that draws the tile as a toroidal map surface.

The saved exploration state is world data, not per-block mutable state. The
block entity only needs placement/orientation and a way to request or receive
the current texture state.

The server owns discovery, persistence, and sync. The client owns mesh
generation, material choice, debug markers, and optional local player markers.
The server should not send projection-center updates.

Start with the Overworld. Nether support can follow once the Overworld object is
stable, because Nether tile size and portal scale are independent from the
Overworld settings.

## Toroid Mapping

Use direct periodic UV mapping instead of a sphere projection:

- `u` maps canonical X from `0..1` around the torus's major ring.
- `v` maps canonical Z from `0..1` around the torus's minor tube.
- Both mesh loops are closed, and both texture axes are periodic.
- The canonical X/Z corner appears where the two torus seams cross.
- There are no poles, hidden antipodes, or viewer-specific chart transitions.

The current placed prototype uses a conventional torus as a large hologram above
a small projector base:

- Center: above the block, not inside the base's collision shape.
- Major radius: large enough for the player to inspect the world map from
  outside the hologram.
- Minor radius: large enough to show terrain color bands on the tube.
- Segments: enough for a smooth object, probably `64 x 24` or similar.
- Normals: generated from the torus parameterization for normal item/block
  lighting.

Placed objects should be stable. Their texture orientation comes from block
facing or a fixed default, not from the current viewer. A player walking around
the object should inspect different physical parts of the same torus, not cause
the map to reproject.

Held objects still need a separate design pass. The likely direction is a small
portable torus preview with an explicit local-player marker, but it may need a
different scale, orientation, or simplified visual treatment from the placed
projector.

Chosen toroid direction:

- Use a small projector base with a large non-colliding hologram torus above it.
- Render the placed projector's outside surface only for ordinary gameplay.
- Let right-click swap which canonical axis occupies the torus major ring.
- Keep the `F3+Y` 2x2 comparison grid as a temporary debug view.

Open toroid questions:

- Should placed toroids use block facing to choose where `u=0` appears?
- How should the current player's canonical position render on placed and held
  toroids without making the map hard to read?
- Should seam rings be subtly marked so players can read the topology, or should
  seams be invisible when the sampled map wraps cleanly?
- Should unexplored regions appear as parchment/blank, dark fog, ocean blue, or
  vanilla map-like empty pixels?

## Exploration State

Add a saved state keyed by dimension and world topology:

- `tileSizeBlocks`: the effective tile width for the dimension when the state
  was created.
- `resolution`: fixed texture resolution, probably starting at `512x512`.
- `discovered`: bitset or byte mask for revealed pixels.
- `colors`: compact color buffer using vanilla map colors or an internal
  palette.
- `version`: for future format changes.

Do not store one pixel per block for large tiles. Use a fixed or bounded
resolution and map canonical X/Z into texture coordinates. A 512-square texture
is enough for a first version and keeps network updates, disk saves, and client
uploads manageable.

The server should update exploration opportunistically:

- On player tick, canonicalize the player's X/Z in the effective dimension.
- Reveal a small radius around the player's canonical position.
- Refresh affected pixels using vanilla map-color logic where possible.
- Rate-limit work by spreading pixel refresh over ticks.
- Mark dirty only when a pixel's discovered state or color changes.

The state should survive alias movement. Exploring an alias of a block reveals
the canonical pixel owned by that block.

The current implementation still raster-fills the canonical tile for projection
testing. Replace that with map-like discovery before treating the object as a
real survival feature:

- Reveal pixels around player canonical positions as they move.
- Keep unknown regions visually distinct on the hologram.
- Refresh discovered colors from vanilla map-color sampling or a cheaper
  equivalent.
- Send dirty patches instead of full snapshots once automatic updates are active.
- Preserve discovered state across reloads and topology changes where possible.

## Block And Item

The existing globe item/block pair remains useful, with `Atlas Projector` as
the in-game name for the original iron-lantern variant:

- The items place blocks with horizontal facing.
- The block has a block entity for client sync and renderer attachment.
- Breaking the block drops the item.
- The placed block is mostly decorative; it should not duplicate map state in
  item NBT for the first version.

Possible interactions:

- Right-click swaps which canonical axis occupies the torus major ring.
- A later interaction can open a larger map/configuration screen once the visual
  language settles.
- Comparator output can be the discovered percentage, if that feels useful
  later.

Keep the first version simple: place, render, break, and optionally report
discovered percentage in a tooltip or debug screen.

The Atlas Projector family has three color variants: iron, copper, and soul.
They are visual variants of the same map projector, not dimension-specific
objects. Each uses the same recipe silhouette:

```text
Empty           Filled Map       Empty
Copper Nugget   Amethyst Shard   Copper Nugget
Copper Ingot    Lantern Type     Copper Ingot
```

This keeps the center column readable as map, focusing crystal, and projector
light, while the side materials stay light enough for an object that can be
held as well as placed. Normal lanterns craft the white Atlas Projector, copper
lanterns craft the warm Copper Atlas Projector, and soul lanterns craft the blue
Soul Atlas Projector. The copper recipe accepts the copper lantern family,
including oxidized and waxed variants.

## Client Rendering

Replace the sphere renderer with a toroid renderer:

- Rename or replace `GlobeSphereMesh` with `GlobeToroidMesh`.
- Render a small projector base and a large non-colliding torus hologram above
  the placed block.
- Upload the exploration color buffer as a dynamic texture.
- Draw unknown pixels distinctly from discovered pixels.
- Map texture `u/v` directly to torus major/minor angles.
- Render the outside surface only, with a mostly visible hologram translucency.
- Keep a temporary `F3+Y` comparison grid for choosing texture side and axis
  mapping: front-left outside X-major/Z-minor, front-right inside
  X-major/Z-minor, back-left outside Z-major/X-minor, and back-right inside
  Z-major/X-minor.
- Remove the viewer-specific projection session and six-anchor chart selection.
- Add optional debug overlays for the X seam, Z seam, seam intersection, and
  local player canonical position.

The physical object does not need to be world-scale. The circumference rule is a
texture rule: tile width corresponds to one full wrap around each torus loop.
The block model can remain a normal placed object.

The renderer should remain object-space and should look acceptable when Globe
World terrain curvature is enabled or disabled. It no longer needs to share
curved-terrain projection math with `GlobeCurvatureShader`.

## Networking

Avoid sending the full texture every tick.

Suggested protocol:

- Send a compact initial snapshot when a client starts watching a globe block or
  opens the larger map view.
- Send dirty rectangular patches or changed pixel runs afterward.
- Include dimension/topology identifiers so stale updates are ignored after
  world changes or setting mismatches.
- Let clients cache the latest texture per dimension during the play session.
- Do not send projection-center updates from the server; clients can derive
  local player markers from their own canonical position.

For the first prototype, a full snapshot on block-entity sync is acceptable if
the texture is small and updates are manual or infrequent. Before release, move
to patch updates so several placed globes do not spam clients.

## Implementation Steps

Already useful and keep:

- Registered the original `globe` block/item plus copper and soul Atlas
  Projector variants.
- Minimal block entity and client renderer hook for placed globes.
- Shared lantern-like held, inventory, and placed projector models with an
  amethyst focus.
- Creative-tab, language, and loot-table resources.
- `GlobeMapSavedData` as Overworld world data for a 512x512 canonical-tile
  texture.
- Temporary raster-fill of the canonical tile for projection testing.
- Full `GlobeMapSnapshotPayload` snapshots to clients on join and revision
  changes.
- Client dynamic texture upload through `GlobeMapTextureCache`.
- First toroid mesh submission through `GlobeToroidMesh`, with direct canonical
  X/Z texture mapping.
- Debug comparison grid for side-by-side inside/outside texture winding and
  X-major versus Z-major wrap-axis tests.
- Placed projector prototype with persisted wrap-axis mode, base-only collision,
  light emission, shared copper/amethyst model rendering, and a large
  outside-surface torus hologram.
- Survival shaped recipes using a filled map, amethyst shard, lantern variant,
  copper nuggets, and copper ingots.

Retire or replace:

- The smooth generated sphere mesh.
- Player-centered front-disk sphere projection.
- Placed-object viewer-local projection sessions. Removed from the placed
  renderer.
- Six-anchor chart selection and debug markers. Removed from the placed
  renderer.
- Any validation criteria based on hiding sphere distortion.

Next steps:

1. Delete or fully retire `GlobeSphereMesh` once no transitional references
   remain.
2. Add a player-position marker for placed and held toroids.
3. Design held-item behavior: stable miniature, player-forward orientation, or a
   simplified readable preview.
4. Replace the temporary raster-fill with real player exploration updates that
   fill in like a map.
5. Choose the final default wrap-axis mapping after in-world testing.
6. Tune the projector base, hologram size, transparency, and interaction
   feedback.
7. Add torus debug markers for X seam, Z seam, seam crossing, and player
   canonical position.
8. Add optional item tooltip or debug command output for discovered percentage.
9. Document shipped toroid behavior in `docs/mod-mechanics/` and keep only
   unresolved exploration or renderer questions in this plan.

## Vanilla Sources To Inspect

Use the Loom common and client-only source jars before choosing method names:

- `net.minecraft.world.item.MapItem`
- `net.minecraft.world.level.saveddata.maps.MapItemSavedData`
- `net.minecraft.world.level.material.MapColor`
- Vanilla block entity renderer registration and render state classes.
- Dynamic texture or map texture upload classes in the client-only sources.
- Block/item registration patterns for Fabric 26.1.2.

## Validation

Use small and medium wrapped Overworld tiles.

Functional checks:

- Place, break, save, reload, and rejoin with a placed globe.
- Explore across X seam, Z seam, and corner seam; only canonical pixels should
  reveal.
- Put two globes in different aliases; both should show the same discovered
  tile state.
- Verify unknown areas remain unknown after client reconnect until the server
  sends the saved state.
- Confirm the globe remains stable when the player crosses tile aliases.

Rendering checks:

- Confirm the torus shows a continuous major loop for canonical X.
- Confirm the torus shows a continuous minor loop for canonical Z.
- Confirm both texture seams meet cleanly at the torus seam crossing.
- Confirm a placed torus does not reproject or scroll when the viewer walks
  around it.
- Confirm the player-position marker is readable from outside the hologram and
  while standing inside the torus.
- Confirm held and inventory rendering fit inside expected item bounds.
- Confirm held behavior clearly communicates the player's canonical position.
- Check the object with Globe World terrain curvature on and off.
- Check near, medium, and far view distances.
- Check multiplayer with two players exploring different parts of the tile.

Performance checks:

- Measure server tick cost while sprinting, flying, and teleporting.
- Measure snapshot and patch payload sizes.
- Confirm several placed globes do not cause duplicate full texture uploads.

## Risks

- A torus is less immediately "globe-like" than a sphere, even though it is more
  accurate to the wrapped world.
- The tube can visually hide itself from some angles; marker and seam design may
  matter more than on a flat map.
- Dynamic texture updates can become noisy in multiplayer without patch-based
  sync.
- Vanilla map color sampling may be too expensive if run at high resolution or
  too often.
- Client renderer APIs may drift during the planned Minecraft 26.2 upgrade, so
  keep rendering hooks small and isolated.

## Done Criteria

- An Atlas Projector item can place a visible toroidal world-map block.
- The projector variants have survival crafting recipes.
- The block renders discovered canonical tile data as a torus with X and Z as
  the two independent loops.
- The placed object is stable in object space and does not depend on the
  viewer's current camera angle.
- Placed and held renderers can show the local player's canonical position.
- The held item remains readable and has an intentional behavior distinct from
  the large placed projector.
- Exploration updates automatically from player movement and persists in saved
  world data.
- The map uses canonical X/Z, so aliases do not duplicate or offset explored
  regions.
- Multiplayer clients receive the same discovered state without excessive
  packet traffic.
- The durable behavior is documented in `docs/mod-mechanics/`.
