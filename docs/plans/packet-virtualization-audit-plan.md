# Packet Virtualization Audit Plan

## Problem

Globe World relies on server packets being relabeled from canonical coordinates
to player-visible alias coordinates. The current implementation covers the most
important chunk, block, block entity, and entity position packets, but Minecraft
has many other position-bearing packets.

Any missed packet can leak canonical coordinates to the client, causing visual
desync, sounds/particles/events at the wrong copy, stale aliases, or broken
interactions near tile edges.

## Current Coverage

- Full chunk with light packets are relabeled by `PlayerChunkSenderMixin` and
  `ClientboundLevelChunkWithLightMixin`.
- Block update, section block update, and block entity data packets are handled
  by `BlockPacketUtil`.
- Add entity, teleport entity, and entity position sync packets are handled by
  `EntityPacketUtil`.
- Entity tracking uses wrapped distance and nearest virtual chunk checks.

## Goals

- Create a maintained inventory of position-bearing game packets.
- Classify each packet as covered, safe, needs wrapping, or intentionally
  ignored.
- Add helper APIs so new packet handling follows one pattern.
- Keep the audit tied to Minecraft 26.1.2 source anchors.

## Proposed Implementation

1. Inventory vanilla game packets.
   - Inspect the local common and client-only sources under the Loom cache.
   - Search `net.minecraft.network.protocol.game` for packet classes containing
     `BlockPos`, `ChunkPos`, `SectionPos`, X/Z fields, entity positions, or
     encoded sound/particle/event positions.

2. Create a vanilla mechanics reference page.
   - Add `docs/vanilla-mechanics/position-bearing-packets.md`.
   - Include packet class names, fields, when vanilla sends them, and relevant
     source anchors.

3. Create a mod mechanics coverage table.
   - Add or extend a table in `docs/mod-mechanics/client.md`.
   - Columns: packet, coordinate type, current status, implementation file,
     risk, and test note.

4. Prioritize missing packets.
   - High priority: light updates, level events, block events, particles, sounds,
     biome resend packets, map/marker packets, and any packet that mutates client
     world state.
   - Medium priority: cosmetic packets that are merely misplaced.
   - Low priority: packets whose positions are already viewer-relative or not
     relevant to tiled dimensions.

5. Implement wrappers incrementally.
   - Add packet-specific copy/relabel helpers.
   - Route common chunk-holder/player sends through those helpers where possible.
   - Avoid a broad blind rewrite of all packets without packet-specific
     semantics.

6. Add regression scenarios.
   - For each covered packet, write a manual or automated test scenario near a
     tile edge.
   - Keep expected behavior in the coverage table.

## Validation

- Use search output from the Loom source jars to prove the packet inventory is
  complete for Minecraft 26.1.2.
- Exercise sounds, particles, block events, level events, biome resends, light
  changes, and entity teleports across all four edges.
- Test both canonical and alias player positions.
- Re-run the audit after Minecraft version bumps.

