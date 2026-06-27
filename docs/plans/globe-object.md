# Globe Object Plan

This plan describes a placeable globe object that acts like a persistent map of
the whole canonical tile. The object should gradually reveal the tile as players
explore, then render the discovered tile surface on a visible globe.

The intended player-facing behavior is:

- Players can craft or obtain a globe item and place it as a block.
- A placed globe shows the explored parts of the current world's canonical tile.
- Exploration progresses automatically while players move through the world,
  instead of requiring a held map update loop.
- The visible globe uses the tile width as the circumference of the projected
  world, so walking one tile width in X corresponds to one full turn around the
  globe.
- The globe uses the same visual idea as Globe World's terrain curvature: the
  tile gets visually denser toward the sides/back of the globe, and the worst
  distortion is hidden on the far side.
- Each client projects the server-owned tile map from that player's position,
  so multiplayer players can see the same explored data through different
  local projections.

## Design Shape

Treat the globe as three separate systems:

1. A server-owned exploration state for the canonical tile.
2. A placeable block and block entity that exposes that state to clients.
3. A client renderer that draws a globe and projects the tile map onto it from
   the local player's view/projection center.

The saved exploration state is world data, not per-block mutable state. Multiple
placed globes in the same dimension should normally show the same discovered
tile map. The block entity only needs placement/orientation and a way to request
or receive the current texture state.

The server does not decide how the tile is wrapped onto the visible globe for
each viewer. It owns discovery, persistence, and sync. The client owns the
camera/player-relative projection and can update that projection every frame
without changing server data.

Start with the Overworld. Nether support can follow once the Overworld object is
stable, because Nether tile size and portal scale are independent from the
Overworld settings.

## Projection Model

The globe should use a miniature version of Globe World's curved-terrain visual
model, not a normal atlas globe projection. Conceptually, the client projects a
flat canonical tile map onto a front-facing sphere cap from a player-relative
viewpoint:

- The player's current canonical tile position is the projection center.
- The front of the globe shows the nearby canonical tile region at the clearest
  density.
- Regions farther from the projection center curve around the sides and become
  visually denser.
- The most distorted/compressed part of the tile lies on the hidden back side of
  the globe.
- One tile width corresponds to the full circumference of the globe projection.

This should make the globe look like the curved terrain already looks to the
player, but compressed into an object. It also avoids forcing the rectangular
tile through a latitude/longitude model where the Z edges become poles.

Use a front-disk projection for the visible surface rather than longitude and
latitude UVs. The foremost normal samples the projection center. Angular
distance from the front maps to radial tile distance, so a full great-circle
turn corresponds to one tile width. The only unavoidable projection singularity
is placed at the back of the sphere, where compression and wrapping artifacts
are acceptable. The dynamic texture should be sampled with continuous/repeating
UVs rather than per-vertex `0..1` wrapping, so tile-edge crossings interpolate
locally on the front side.

The projection is client-side and viewer-specific. The exploration texture
remains stable; only the transform from canonical tile X/Z to the globe surface
changes for each client.

Held and placed globes should anchor the projection differently:

- Held globe: the foremost point of the globe tracks the holder's current
  canonical tile position. As the player moves, the tile projection scrolls so
  the player's position stays at the front.
- Placed globe: when viewed by a player, that player's current canonical tile
  position faces toward that player. The globe then behaves as a stationary
  object in world space while they walk around it, so walking to the back side
  reveals the far side of that projected globe instead of continuously rotating
  the same front face toward the camera.

For placed globes, the projection basis should be captured per client/viewer
when the globe enters view or when the renderer establishes a stable viewing
session. After that, camera movement around the object should reveal different
sides of the same local projection. The projection may need a refresh rule if
the player moves far enough through the world while continuing to watch the same
placed globe.

Open projection questions:

- What exact math should the object renderer share with
  `GlobeCurvatureShader`/curved raycast helpers so the globe's miniaturized
  terrain illusion matches the world illusion?
- For placed globes, when should the viewer-specific projection anchor refresh:
  on first view, on interaction, after the player moves a threshold distance, or
  only when the block is reloaded?
- Should the placed globe also have a fixed decorative spin/orientation, or
  should it stay fully locked to the captured projection basis?
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

## Block And Item

Add a new globe item/block pair:

- The item places a block with horizontal facing.
- The block has a block entity for client sync and renderer attachment.
- Breaking the block drops the item.
- The placed block is mostly decorative; it should not duplicate map state in
  item NBT for the first version.

Possible interactions:

- Right-click opens a larger globe/map screen.
- Sneak right-click toggles rotation mode or orientation.
- Comparator output can be the discovered percentage, if that feels useful
  later.

Keep the first version simple: place, render, break, and optionally report
discovered percentage in a tooltip or debug screen.

## Client Rendering

Use a custom block entity renderer for the placed globe:

- Render a sphere mesh inside the block bounds.
- Upload the exploration color buffer as a dynamic texture.
- Draw unknown pixels distinctly from discovered pixels.
- For held items, continuously center the projection on the holder's canonical
  tile position.
- For placed blocks, capture a viewer-specific projection basis that puts the
  viewer's current canonical tile position on the side initially facing them,
  then keep the object stationary as the viewer walks around it.
- Sample the texture through the curved-terrain-style front-disk projection,
  with the densest/distorted regions hidden around the far side.

The physical globe does not need to be world-scale. The circumference rule is a
projection rule: tile width corresponds to one full wrap of the texture around
the sphere. The block model can remain a normal placed object.

The renderer should reuse the same conceptual curvature math as the world
renderer where possible, but it should remain an object-space renderer. It
should look acceptable when Globe World terrain curvature is enabled or disabled.

## Networking

Avoid sending the full texture every tick.

Suggested protocol:

- Send a compact initial snapshot when a client starts watching a globe block or
  opens the larger globe view.
- Send dirty rectangular patches or changed pixel runs afterward.
- Include dimension/topology identifiers so stale updates are ignored after
  world changes or setting mismatches.
- Let clients cache the latest texture per dimension during the play session.
- Do not send projection-center updates from the server; each client can derive
  them from its own canonical player position and local view of the globe.

For the first prototype, a full snapshot on block-entity sync is acceptable if
the texture is small and updates are manual or infrequent. Before release, move
to patch updates so several placed globes do not spam clients.

## Implementation Steps

Stage 1 and the first visual refinement were blank shell object work:

- Added a registered `globe` block/item.
- Added a smooth generated sphere mesh that renders both placed and held.
- Added a minimal block entity and client renderer for placed globes.
- Added a special item renderer for held and inventory globes.
- Added creative-tab, language, and loot-table resources.

The current projection-test pass also:

- Adds `GlobeMapSavedData` as Overworld world data for a 512x512 canonical-tile
  texture.
- Fills the texture by raster-scanning the canonical tile on server ticks,
  rather than requiring player exploration. This is temporary so small test
  worlds can validate projection and projection-center behavior.
- Sends full `GlobeMapSnapshotPayload` snapshots to watching clients on join
  and when the saved map revision changes.
- Uploads the snapshot into a client dynamic texture.
- Projects that texture on placed and held globes with a player-centered
  curved sphere projection.

1. Inspect vanilla map saved data, map color sampling, dynamic texture upload,
   block entity renderer registration, and block/item registration in the
   Minecraft 26.1.2 sources. Done.
2. Add registration scaffolding for the globe block, item, block entity type,
   and client renderer. Done.
3. Add a minimal static renderer with a placeholder generated texture so the
   placed object can be validated before saved data exists. Done.
4. Add `GlobeMapSavedData` or similar server state for discovered pixels and
   colors. Done for the Overworld projection-test texture.
5. Add player-tick exploration updates using canonical X/Z from
   `TopologyContexts` or `CoordUtil`. Shelved temporarily; the current build
   raster-fills the tile for projection testing.
6. Add snapshot sync from server to clients and dynamic texture upload on the
   client. Done with full snapshots.
7. Replace the placeholder renderer texture with synced exploration data. Done.
8. Add held-item projection centered on the holder's canonical position. First
   pass done for the local client.
9. Add placed-block projection with a stable viewer-specific basis and tune the
   sphere mesh. First pass done with a captured viewer-local center/facing and
   front-disk sphere projection.
10. Add optional item tooltip or debug command output for discovered percentage.
11. Document shipped behavior in `docs/mod-mechanics/` and keep any unresolved
    projection experiments in this plan.

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

- Hold the globe while walking and confirm the player's canonical position stays
  at the front of the object.
- View a placed globe, then walk around it and confirm it behaves as a
  stationary object whose far side can be inspected.
- Confirm the densest/distorted part of the tile remains on or near the hidden
  back side from the intended projection anchor.
- Check the object with Globe World terrain curvature on and off.
- Check near, medium, and far view distances.
- Check multiplayer with two players exploring different parts of the tile.

Performance checks:

- Measure server tick cost while sprinting, flying, and teleporting.
- Measure snapshot and patch payload sizes.
- Confirm several placed globes do not cause duplicate full texture uploads.

## Risks

- A sphere-like object cannot show the entire rectangular tile at uniform
  density. The design relies on hiding the worst compression around the back.
- Placed globes need a stable per-viewer projection basis; if it refreshes too
  often the object will feel like it is billboarded instead of stationary.
- Vanilla map color sampling may be too expensive if run at high resolution or
  too often.
- Dynamic texture updates can become noisy in multiplayer without patch-based
  sync.
- Client renderer APIs may drift during the planned Minecraft 26.2 upgrade, so
  keep rendering hooks small and isolated.

## Done Criteria

- A globe item can place a visible globe block.
- The block renders discovered canonical tile data through the same broad
  visual model as Globe World's curved terrain.
- The held globe keeps the holder's current canonical tile position at the
  front.
- The placed globe uses a stable viewer-specific projection so players can walk
  around it and see the far side.
- Exploration updates automatically from player movement and persists in saved
  world data.
- The globe uses canonical X/Z, so aliases do not duplicate or offset explored
  regions.
- Multiplayer clients receive the same discovered state without excessive
  packet traffic.
- The durable behavior is documented in `docs/mod-mechanics/`.
