# Atlas Projector Discovery Plan

Atlas Projectors now work as the toroidal world-map object for Globe World's
wrapped rectangular tile. The remaining feature is progressive discovery:
canonical tile pixels should stay hidden until players explore nearby world
space.

## Current Shipped Behavior

- The original `globe` block/item id remains for compatibility, with `Atlas
  Projector` as the player-facing name.
- Iron, copper, and soul Atlas Projectors can be crafted, placed on floors,
  walls, and ceilings, broken, and dropped.
- A placed projector stores its attachment face, horizontal facing, and
  projection enabled flag. Right-click toggles the hologram projection on or
  off.
- The client renders a large non-colliding torus hologram from the placed block
  entity. Canonical X maps around the torus major ring; canonical Z maps around
  the minor tube.
- The torus uses server-owned `GlobeMapSavedData` for a `512x512` canonical
  Overworld texture. Clients receive full `GlobeMapSnapshotPayload` snapshots
  on join and revision changes, then upload the buffer through
  `GlobeMapTextureCache`.
- Unknown pixels are already represented in the saved `discovered` bitset and
  are rendered with the client unknown color.
- For projection testing, the server currently raster-fills the whole canonical
  tile through `GlobeMapSavedData.fillNextPixels(...)`. This is scaffolding, not
  final survival behavior.

## Remaining Feature

Replace background full-tile reveal with player-driven exploration:

- On server player ticks, canonicalize each exploring player's X/Z position in
  the effective dimension.
- Convert the canonical position and reveal radius into one or more texture
  pixel ranges.
- Sample and mark only those nearby pixels as discovered.
- Preserve alias semantics: exploring any alias reveals the canonical pixels
  owned by that location, without duplicating or offsetting discoveries.
- Keep unknown regions hidden across save/reload and client reconnect until the
  server sends discovered pixels.
- Rate-limit sampling so sprinting, flying, teleporting, and multiplayer
  exploration do not spike server tick time.

The temporary `fillNextPixels(...)` path should be retired or converted into a
debug/admin tool once player-driven discovery is active.

## Data And Sync Direction

The existing data shape is still the right base:

- `tileSizeBlocks`: topology width used when the state was created.
- `resolution`: fixed texture resolution, currently `512x512`.
- `discovered`: bitset for revealed pixels.
- `colors`: compact vanilla-map-color buffer.
- `version`: future format changes.

Full snapshots are acceptable during the prototype. Before treating this as a
finished multiplayer feature, add dirty patch or changed-run sync so several
players exploring different regions do not resend the full texture each time.

## Validation

- New projector worlds should start mostly unknown.
- Walking, flying, and teleporting should reveal only nearby canonical pixels.
- Crossing an X seam, Z seam, or corner seam should continue discovery on the
  correct canonical pixels.
- Two projectors in different aliases should show the same discovered state.
- Saving, reloading, and reconnecting should preserve discovered and unknown
  regions.
- Multiplayer clients should converge on the same discovered state without
  excessive packet traffic.
