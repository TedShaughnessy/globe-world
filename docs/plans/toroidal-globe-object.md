# Atlas Projector Discovery Plan

Atlas Projectors now work as the toroidal world-map object for Globe World's
wrapped rectangular tile. Progressive shared discovery is implemented; this
note tracks remaining hardening work for sync efficiency and tuning.

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
- Held Atlas Projector items keep the projector model in hand and render the
  shared discovery texture above it as a small translucent, player-centered,
  heading-up circular map window.
- The torus uses server-owned `GlobeMapSavedData` for a `512x512` canonical
  Overworld texture. Clients receive full `GlobeMapSnapshotPayload` snapshots
  on join and revision changes, then upload the buffer through
  `GlobeMapTextureCache`.
- Unknown pixels are represented in the saved `discovered` bitset and upload to
  the client as fully transparent texture pixels.
- `GlobeMapSavedData.revealAround(...)` canonicalizes an exploration center,
  converts a block radius into toroidal texture pixel bounds, samples unknown
  pixels with the existing map-color path, and advances the saved-data revision
  when discovery changes.
- `GlobeMapTracker` scans non-spectator Overworld players every 5 ticks,
  reveals a 48 block radius around their canonical X/Z, skips redundant work
  until players move 8 canonical blocks or a 200 tick refresh window passes,
  and caps each reveal pass at 20,000 newly sampled pixels.
- Discovery fills tiny single-pixel holes that are surrounded by discovered
  pixels, smoothing over small missed spots after players explore around them.
- Successful canonical Overworld block edits refresh the affected discovered
  projector-map pixel through `GlobeMapTracker.refreshChangedColumn(...)`.
- The old background full-tile fill path has been removed from normal code.

## Remaining Follow-Ups

- Tune reveal radius, movement threshold, refresh cadence, and pixel budget from
  playtesting; consider exposing them as config once the feel is settled.
- Add dirty patch, changed-run, or dirty-rectangle sync so several players
  exploring different regions do not resend the full `512x512` texture each
  sync.
- Consider whether any non-`Level.setBlock(...)` world mutations need explicit
  projector-map refresh hooks after testing.
- Consider grouping players by nearby canonical pixel center in the same tick
  to avoid duplicate sampling in dense multiplayer sessions.
- If projection debug comparison should show holes too, render its comparison
  variants with a translucent render type.

## Data And Sync Direction

The existing data shape is still the right base:

- `tileSizeBlocks`: topology width used when the state was created.
- `resolution`: fixed texture resolution, currently `512x512`.
- `discovered`: bitset for revealed pixels.
- `colors`: compact vanilla-map-color buffer.
- `version`: future format changes.

Discovery is shared world state. It is not per-player and not stored on
individual Atlas Projector block entities. Multiple projectors in different
aliases should therefore always display the same canonical texture.

Full snapshots remain acceptable during the prototype. Before treating this as
a finished multiplayer feature, add dirty patch or changed-run sync so several
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
- Editing blocks in a discovered column should refresh that projector pixel;
  editing blocks in undiscovered space should not reveal it.
