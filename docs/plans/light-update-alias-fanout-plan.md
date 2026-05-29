# Light Update Alias Fanout Plan

## Problem

Full chunk packets are relabeled to alias chunk positions, but incremental light
updates appear to pass through the generic chunk-holder broadcast path without
alias relabeling. The current block packet fanout handles block updates, section
block updates, and block entity data packets only.

As a result, a client can receive later `ClientboundLightUpdatePacket` updates at
the canonical chunk position instead of every visible alias. This can leave light
seams or stale light at tile borders.

## Current Hooks

- `PlayerChunkSenderMixin` relabels `ClientboundLevelChunkWithLightPacket`.
- `ClientboundLevelChunkWithLightMixin` overrides full chunk packet X/Z at write
  time.
- `ChunkHolderMixin` fans out selected broadcast packets through
  `BlockPacketUtil`.
- `BlockPacketUtil` does not currently handle light update packets.

## Goals

- Fan out incremental light updates to every loaded alias of the canonical
  chunk for each player.
- Preserve vanilla behavior for non-tiled dimensions.
- Avoid mutating shared packet instances in ways that affect other players.
- Keep full chunk light behavior unchanged.

## Proposed Implementation

1. Inspect vanilla packet structure.
   - Use the local common sources for `ClientboundLightUpdatePacket` and
     `ClientboundLightUpdatePacketData`.
   - Confirm whether the data payload is reusable for a different visible chunk
     position or must be reconstructed from the light engine.

2. Add a light packet copy or relabel mechanism.
   - Prefer constructing a fresh `ClientboundLightUpdatePacket` for each alias
     if vanilla exposes enough constructor data.
   - If fields are private and immutable, add a small accessor/mixin interface
     similar to `GlobeChunkPacket`.
   - Ensure both X/Z and light-data position assumptions are correct.

3. Extend broadcast fanout.
   - Add `ClientboundLightUpdatePacket` handling to `BlockPacketUtil`.
   - For each player, find aliases through `ChunkAliasTracker`.
   - Emit one light packet per loaded alias.
   - Fall back to nearest virtual chunk if no alias record exists.

4. Keep canonical and alias chunks coherent.
   - Include the canonical chunk if the player has it loaded.
   - Do not send updates for aliases the player has not loaded.

5. Document the mechanic.
   - Update `docs/mod-mechanics/blocks-and-ticks.md` or
     `docs/mod-mechanics/client.md`.
   - Remove or refine the light seam note in `todo.md` after testing.

## Validation

- Place and remove torches near every tile edge and corner.
- Trigger skylight changes by placing/removing opaque blocks near seams.
- Compare debug HUD sky/block light values on both sides of a tile boundary.
- Test with multiple aliases visible at once on a tiny tile.
- Test Nether block light separately from Overworld sky light.

