# Light Update Alias Fanout Plan

Status: implemented, pending manual validation. Durable behavior is documented
in [../mod-mechanics/blocks-and-ticks.md](../mod-mechanics/blocks-and-ticks.md),
[../mod-mechanics/chunks.md](../mod-mechanics/chunks.md), and
[../mod-mechanics/client.md](../mod-mechanics/client.md). Keep this plan until
manual seam validation confirms whether phase-two server-side light propagation
work is needed.

## Problem

Full chunk packets are relabeled to alias chunk positions, but incremental light
updates are still broadcast through vanilla `ChunkHolder.broadcast(...)`.
`ChunkHolderMixin` originally fanned out block, section, and block-entity
packets through `BlockPacketUtil`, while `ClientboundLightUpdatePacket` fell
through unchanged. The implemented fix extends the same fanout path to
incremental light packets.

As a result, a player who has loaded an alias of a canonical chunk can receive a
later light update at the canonical chunk coordinate only. That can leave stale
block light or sky light in visible aliases, especially near tile borders where
the player can see multiple copies of the same canonical chunk.

## Vanilla Facts

Checked against the local Loom common/client sources for Minecraft 26.1.2:

- `ServerChunkCache.onLightUpdate(LightLayer, SectionPos)` finds the holder for
  `pos.chunk()` and schedules it for broadcast.
- `ChunkHolder.sectionLightChanged(...)` stores changed light section indexes in
  `skyChangedLightSectionFilter` and `blockChangedLightSectionFilter`.
- `ChunkHolder.broadcastChanges(LevelChunk)` constructs one
  `ClientboundLightUpdatePacket(chunk.getPos(), lightEngine, skyMask,
  blockMask)` and sends it to border players with `broadcast(...)`.
- `ClientboundLightUpdatePacket` contains final `x`, `z`, and a
  `ClientboundLightUpdatePacketData` object.
- `ClientboundLightUpdatePacketData` copies section masks and 2048-byte
  `DataLayer` payloads at construction time. After construction, its payload is
  not tied to the original `ChunkPos`.
- `ClientPacketListener.handleLightUpdatePacket(...)` reads packet `x/z` and
  queues `applyLightData(x, z, lightData, true)`.
- `ClientPacketListener.applyLightData(...)` applies the same masks and payload
  lists to the client light engine at the packet `x/z`, then marks that chunk's
  light enabled.

Conclusion: a light packet can be copied by reusing its existing light data with
different packet `x/z`. It should not be reconstructed from the light engine for
an alias chunk, because the server light engine stores canonical light data.

## Current Hooks

- `PlayerChunkSenderMixin` records loaded aliases in `ChunkAliasTracker` when
  full chunk packets are sent.
- `PlayerChunkSenderMixin` removes alias records when chunk drop packets are
  sent.
- `ClientboundLevelChunkWithLightMixin` overrides full chunk packet X/Z at write
  time.
- `ChunkHolderMixin` wraps `ChunkHolder.broadcast(...)` sends and calls
  `BlockPacketUtil.virtualizeForLoadedAliases(packet, player)`.
- `BlockPacketUtil` handles `ClientboundBlockUpdatePacket`,
  `ClientboundSectionBlocksUpdatePacket`,
  `ClientboundBlockEntityDataPacket`, and
  `ClientboundLightUpdatePacket`.
- `ClientboundLightUpdatePacketAccessor` invokes the private vanilla decode
  constructor so Globe can copy a light packet with alias `x/z` while reusing
  the copied light-data payload.

## Goals

- Fan out each incremental `ClientboundLightUpdatePacket` to every loaded alias
  of the canonical chunk for the receiving player.
- Preserve vanilla behavior for untiled dimensions and for players that have no
  alias tracker record yet.
- Never mutate a shared vanilla packet instance while it may be sent to multiple
  players.
- Reuse copied light data from the original packet instead of reading alias
  light data from the server light engine.
- Keep full chunk light send behavior unchanged.
- Keep the first implementation narrow. Do not rename `BlockPacketUtil` in this
  change even though it will start handling light packets; consider a later
  cleanup rename such as `ClientWorldPacketUtil` only after packet fanout work
  settles.

## Concrete Implementation Plan

### 1. Add A Light Packet Copy Invoker

Add `mod-fabric/src/main/java/globe/world/mixin/ClientboundLightUpdatePacketAccessor.java`.

Use a mixin `@Invoker("<init>")` for the private vanilla constructor:

- target class: `ClientboundLightUpdatePacket`
- constructor signature: `(FriendlyByteBuf input)`
- method name: `globeWorld$new(FriendlyByteBuf input)`

Register the accessor in `mod-fabric/src/main/resources/globe-world.mixins.json`.

Reason: vanilla has no constructor that accepts `int x`, `int z`, and an
already-copied `ClientboundLightUpdatePacketData`. The private decode
constructor does exactly what is needed if Globe writes a temporary buffer with
the alias `x/z` followed by the original packet's light data.

Mitigation for version brittleness:

- Treat the invoker as the preferred construction path because it follows
  vanilla packet decoding and does not mutate shared packets.
- If the private constructor signature changes, first inspect the new
  `ClientboundLightUpdatePacket` source and target whatever constructor or
  codec path now decodes packet `x/z` plus `ClientboundLightUpdatePacketData`.
- If vanilla removes that decode constructor entirely, fall back to a
  packet-specific accessor/mixin plan that creates a fresh packet instance and
  sets copied `x`, `z`, and `lightData` fields with `@Mutable` accessors. Do not
  fall back to mutating the original broadcast packet.

### 2. Add A Dedicated Light Packet Copier

Extend `BlockPacketUtil` with a helper shaped like:

```java
private static ClientboundLightUpdatePacket copyLightUpdate(
        ClientboundLightUpdatePacket packet,
        ChunkPos visibleChunk) {
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
        buffer.writeVarInt(visibleChunk.x());
        buffer.writeVarInt(visibleChunk.z());
        packet.getLightData().write(buffer);
        return ClientboundLightUpdatePacketAccessor.globeWorld$new(buffer);
    } finally {
        buffer.release();
    }
}
```

Implementation details:

- Import `io.netty.buffer.Unpooled`, `FriendlyByteBuf`,
  `ClientboundLightUpdatePacket`, and the new accessor.
- The copied packet receives fresh `x/z` but a decoded copy of the same masks
  and byte-array payloads.
- The original packet is never modified, so broadcast to other players remains
  independent.
- Keep the `try/finally` release. The vanilla decode constructor reads bitsets,
  lists, and byte arrays immediately, so the returned packet should not retain
  the temporary buffer. Recheck this source assumption during implementation.
- If the implementation hits reader-index surprises, set `buffer.readerIndex(0)`
  before invoking the constructor. The initial reader index should already be
  zero, but this is the first thing to inspect if decoded values look wrong.

### 3. Fan Out Loaded Aliases In `BlockPacketUtil`

Add a light-update branch to both public packet paths:

- `virtualizeFor(...)` should call `virtualizeLightUpdate(...)`.
- `virtualizeForLoadedAliases(...)` should call
  `virtualizeLightUpdateForLoadedAliases(...)`.

The loaded-alias method should mirror section-update fanout:

1. Read the packet chunk position from `packet.getX()` and `packet.getZ()`.
2. Canonicalize with `CoordUtil.wrapChunk(viewer.level(), packet.getX())` and
   `CoordUtil.wrapChunk(viewer.level(), packet.getZ())`.
3. Query `ChunkAliasTracker.aliasesForCanonical(viewer,
   viewer.level().dimension(), canonicalX, canonicalZ)`.
4. Dedupe aliases by `ChunkPos.pack(alias.x(), alias.z())` before creating
   packets. The tracker should already be set-like, but deduping makes the
   packet helper robust against future tracker changes.
5. If aliases exist, return one `copyLightUpdate(packet, alias)` per deduped
   alias.
6. If aliases are empty, return `List.of(virtualizeLightUpdate(packet, viewer))`
   as the same nearest-visible fallback used by block packets.

The nearest-visible fallback should compute:

- `virtualX = CoordUtil.virtualChunk(viewer.level(), canonicalX,
  viewer.chunkPosition().x())`
- `virtualZ = CoordUtil.virtualChunk(viewer.level(), canonicalZ,
  viewer.chunkPosition().z())`

If `virtualX/virtualZ` equal the packet coordinates, return the original packet.
Otherwise return `copyLightUpdate(packet, new ChunkPos(virtualX, virtualZ))`.

Do not filter aliases by border/non-border status here. `ChunkHolder` already
selects the target player set for light updates; once a player is selected, the
packet should match the aliases that player has actually loaded.

If aliases are empty in a tiled dimension and the packet chunk differs from the
nearest virtual chunk, emit a debug-only log. This makes alias-registration races
observable without spamming normal play:

`GW_LIGHT_ALIAS_FANOUT fallback player={} original={} canonical={} virtual={}`

Keep this log behind `GlobeWorld.LOGGER.isDebugEnabled()`.

### 4. Verify The Broadcast Hook Still Covers Light Updates

Keep `ChunkHolderMixin` at `ChunkHolder.lambda$broadcast$0` unless compile or
runtime testing shows the lambda target changed. This hook is broad enough
because vanilla sends light updates, block updates, section updates, and block
entity update packets through the same private `broadcast(...)` helper.

If the mixin target fails after implementation, use the local source anchor:

- `ChunkHolder.broadcast(List<ServerPlayer>, Packet<?>)`
- lambda body: `players.forEach(player -> player.connection.send(packet))`

The replacement still needs player context, so keep the wrap point around
`ServerGamePacketListenerImpl.send(Packet<?>)` where the lambda parameter
`ServerPlayer player` is available.

### 5. Keep Alias Tracker Risk Contained

Light fanout will depend on the same `ChunkAliasTracker` lifecycle guarantees as
block packet fanout. Do not add a second tracker for light updates.

Implementation checks:

- Query aliases with the player's current dimension key, not a raw `ServerLevel`
  reference.
- Use immutable snapshots returned from `aliasesForCanonical(...)`.
- Do not send packets to mathematically possible aliases that are not in the
  tracker. Loaded aliases are the authority for what the client can render.
- If stale alias records are suspected, validate the already implemented
  lifecycle cleanup rather than filtering light packets ad hoc.

### 6. Add Phase-Two Propagation Investigation Criteria

This plan fixes client packet coordinates. If seams remain after successful
light packet fanout, open or extend a separate investigation for server-side
light propagation across canonical tile boundaries.

Phase-two trigger:

- debug logs show light updates are fanned out to the expected aliases
- the client receives/rebuilds light at alias chunk coordinates
- visible or F3 light differences still remain at tile borders

Initial phase-two targets:

- `ServerChunkCache.onLightUpdate(...)` holder lookup for wrapped section
  positions
- `LevelLightEngine.checkBlock(...)`, `runLightUpdates(...)`, and neighbor
  section reads near canonical tile edges
- `LightChunkGetter` and `getChunkForLighting(...)` behavior for cross-edge
  light reads
- sky-light column behavior separately from block-light propagation

### 7. Update Mechanics Documentation

After implementation, update durable docs:

- In `docs/mod-mechanics/blocks-and-ticks.md`, extend the `BlockPacketUtil`
  paragraph to include incremental light update fanout.
- In `docs/mod-mechanics/client.md`, mention that full chunk light data and
  incremental light updates are both delivered at visible alias chunk
  coordinates.
- In `docs/mod-mechanics/README.md`, update the status row if it still implies
  block packet fanout excludes light.

Keep this plan until manual validation confirms the seam issue is fixed. Then
mark it implemented or move any remaining light-edge investigation into a
follow-up plan.

### 8. Clean Up The Known-Seam TODO After Validation

`todo.md` currently notes:

- `light related things, there sometimes a light difference seam at tile borders`

Do not remove this line during the first code change. Remove or refine it only
after manual validation proves whether the visible seam was caused by stale
incremental light packets or by a separate sky/block light propagation issue.

## Validation

### Build Checks

Ask the user to run:

- `./gradlew build`

Expected compile-sensitive points:

- the new `ClientboundLightUpdatePacketAccessor` is registered in the mixin JSON
- the invoker signature matches the private `FriendlyByteBuf` constructor
- the temporary buffer is released after packet decode and does not cause
  retained-buffer use
- `BlockPacketUtil` imports do not conflict with generic `Packet<?>` return
  types

### Manual Scenarios

Use a small tile size so multiple aliases of the same canonical chunk are
visible at once.

1. Place and remove torches near each X edge, Z edge, and tile corner.
2. Place and remove opaque blocks near seams to trigger sky-light changes.
3. Repeat from the canonical tile and from at least one non-canonical alias.
4. Compare F3 sky/block light values and visible lighting across both sides of
   a tile boundary.
5. Test with two players standing in different aliases of the same canonical
   chunk; each player should receive updates at their own loaded aliases.
6. Test the Nether separately, because it exercises block light without
   Overworld sky light.
7. Disconnect and reconnect near a seam, then trigger another light update.
8. Respawn and dimension-transfer, return to a visible alias, then trigger
   another light update. This checks that stale alias tracker records do not
   leak into light fanout.

### Useful Debug Checks

Temporary debug logging can be added around the new light branch if needed:

- original packet chunk
- canonical chunk
- aliases returned by `ChunkAliasTracker`
- deduped alias count
- number of copied light packets sent for the player
- whether the nearest-visible fallback was used

Remove noisy logs before keeping the implementation unless they are guarded by
`GlobeWorld.LOGGER.isDebugEnabled()`.

## Risks And Follow-Ups

- If a player receives a light update before its full chunk packet records an
  alias, the nearest-visible fallback sends one best-effort packet rather than
  full alias fanout. That matches existing block packet behavior, and the
  debug-only fallback log should show whether this happens often enough to need
  a stronger ordering fix.
- If seams remain after packet fanout, use the phase-two criteria above to
  investigate server-side light propagation across canonical tile boundaries
  rather than continuing to adjust packet relabeling.
- Light updates are sent to border players in vanilla, so validation should
  include chunks at the edge of the player's watch distance.
- More visible aliases means more light packets. Limit fanout to
  `ChunkAliasTracker` records only and dedupe before copying packets; do not fan
  out to every mathematically possible tile alias.
