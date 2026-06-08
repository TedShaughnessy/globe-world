# Chest Lid Alias Sync

## Status

Planned. Chests can remain visually open in tiled worlds because vanilla chest
lid state is synchronized through block events, while Globe World's loaded-alias
fanout currently covers ordinary block, block-entity, section, and light update
packets.

## Symptom

During playtesting, a chest in a tiled world got stuck open after use. The
canonical server block entity may already be closed, but one rendered client
alias keeps its lid open until the chunk or block entity is reloaded or another
matching block event reaches that exact alias position.

## Vanilla Mechanics

Vanilla chest lid animation is driven by `ChestBlockEntity` open-count block
events:

- `ChestBlockEntity.startOpen(...)` increments `ContainerOpenersCounter`.
- `ChestBlockEntity.stopOpen(...)` decrements it.
- `ChestBlockEntity.signalOpenCount(...)` calls
  `level.blockEvent(pos, block, 1, currentOpenCount)`.
- `ServerLevel.runBlockEvents(...)` sends a
  `ClientboundBlockEventPacket(pos, block, 1, currentOpenCount)` through
  `PlayerList.broadcast(...)`.
- The client applies the packet at its exact `BlockPos`; for chests,
  `triggerEvent(1, count)` sets the local lid controller open when
  `count > 0`.

This is not block state, inventory NBT, or a normal block-entity data packet.
A client-side alias copy that misses the close block event has no later
automatic reconciliation.

## Current Globe Behavior

Covered paths:

- `BlockPacketUtil.virtualizeForLoadedAliases(...)` fans out
  `ClientboundBlockUpdatePacket`, `ClientboundBlockEntityDataPacket`,
  `ClientboundSectionBlocksUpdatePacket`, and `ClientboundLightUpdatePacket` to
  every loaded alias tracked by `ChunkAliasTracker`.
- `ChunkHolderMixin` routes chunk-holder block update broadcasts through that
  loaded-alias fanout.

Gap:

- `WorldEventPacketUtil.virtualizeBlockEvent(...)` virtualizes
  `ClientboundBlockEventPacket` to a single viewer-nearest alias.
- `PlayerListBroadcastMixin` sends exactly that one virtualized packet for
  world-event broadcasts.
- If the open event and close event are virtualized to different aliases, or if
  multiple aliases of the same canonical chest are loaded, the stale alias can
  keep rendering as open.

Secondary risk:

- `ContainerOpenersCounter.getEntitiesWithContainerOpen(...)` builds a raw
  `AABB` around the canonical block-entity position and asks
  `Level.getEntities(...)` for candidates.
- In a tiled world, a player can have the chest menu open while standing near a
  visual alias far from the canonical block position.
- `Player.hasContainerOpen(container, blockPos)` only checks whether the current
  menu belongs to that container. The raw candidate search decides whether the
  player is seen during the opener recheck.

## Goal

Keep all loaded client aliases of a canonical container lid in the same open
state, while keeping the canonical server open count single-owned and avoiding
duplicate server-side container side effects.

## Non-Goals

- Do not create separate per-alias chest block entities on the server.
- Do not change inventory storage, loot unpacking, comparator output, or menu
  ownership.
- Do not make every cosmetic world-event packet fan out to every loaded alias in
  this pass. Start with block events because they mutate client-side block
  entity presentation state.

## Implementation Plan

### 1. Add Loaded-Alias Fanout For Block Events

Add a loaded-alias path for `ClientboundBlockEventPacket`.

Suggested shape:

```java
public static List<Packet<?>> virtualizeForLoadedAliases(Packet<?> packet, ServerPlayer viewer)
```

in `WorldEventPacketUtil`, mirroring `BlockPacketUtil` but handling only packets
owned by the world-event broadcast path.

For `ClientboundBlockEventPacket`:

1. Canonicalize the packet position with `CoordUtil.wrapBlockPos(...)`.
2. Derive the canonical chunk X/Z from the canonical position.
3. Query `ChunkAliasTracker.aliasesForCanonical(...)` for the receiving player.
4. If there are no loaded aliases, fall back to the existing
   viewer-nearest `virtualizeBlockEvent(...)`.
5. For each loaded alias, offset the canonical block position into that alias
   chunk and create a new `ClientboundBlockEventPacket` with the same block,
   `b0`, and `b1`.
6. Dedupe by final block position to avoid duplicate packets if tracker state is
   ever redundant.

The offset logic should match `BlockPacketUtil`:

```java
dx = (alias.x - canonicalChunkX) * 16
dz = (alias.z - canonicalChunkZ) * 16
visiblePos = canonicalPos.offset(dx, 0, dz)
```

Keep the helper local to `WorldEventPacketUtil` or factor a small shared
position-offset helper if duplication with `BlockPacketUtil` starts to grow.

### 2. Route Broadcast Block Events Through The Fanout

Update `PlayerListBroadcastMixin.broadcastWorldEventWithWrappedDistance(...)`.

Current behavior:

```java
player.connection.send(WorldEventPacketUtil.virtualizeFor(packet, player));
```

New behavior:

```java
for (Packet<?> virtualPacket : WorldEventPacketUtil.virtualizeForLoadedAliases(packet, player)) {
    player.connection.send(virtualPacket);
}
```

The existing wrapped-distance gate should remain. A player should only receive
the event when they are near the event in toroidal space, but once eligible, all
their loaded aliases for the affected canonical chunk should be updated.

For non-block-event packets, `virtualizeForLoadedAliases(...)` can simply return
`List.of(virtualizeFor(packet, viewer))`.

### 3. Add Alias-Aware Container Opener Rechecks

Add a mixin on `ContainerOpenersCounter.getEntitiesWithContainerOpen(...)`.

Suggested file:

- `mod-fabric/src/main/java/globe/world/mixin/ContainerOpenersCounterMixin.java`

Suggested mixin target:

- Wrap the `Level.getEntities(Entity, AABB, Predicate)` invocation inside
  `ContainerOpenersCounter.getEntitiesWithContainerOpen(...)`.

Behavior:

1. Call vanilla `Level.getEntities(...)` first and keep its result. This
   preserves canonical-frame behavior and any non-player `ContainerUser`
   compatibility.
2. If the level is not a tiled `ServerLevel`, return vanilla's result.
3. For each `ServerPlayer` in the same level:
   - Skip players already returned by vanilla.
   - Build the player-nearest alias of the search box using
     `CoordUtil.virtualAabb(level, searchBox, player.getX(), player.getZ())`.
   - Include the player if their bounding box intersects that virtualized search
     box and the vanilla predicate accepts the player.
4. Return a deduped mutable list of entities.

This keeps opener rechecks from dropping a player who is raw-far from the
canonical chest but topologically near the visible alias they opened.

Register the mixin in `mod-fabric/src/main/resources/globe-world.mixins.json`.

### 4. Keep Interaction And Storage Canonical

Do not change:

- `LevelSetBlockBroadcastMixin` canonicalization for block entity lookup.
- `ServerGamePacketListenerImplMixin` client action canonicalization.
- `PlayerInteractionRangeMixin` wrapped block interaction range.

Those paths already make alias interaction operate on the canonical chest. The
new work should only fix lid/event presentation and opener recheck candidates.

## Files To Change

- `mod-fabric/src/main/java/globe/world/util/WorldEventPacketUtil.java`
  - Add loaded-alias fanout for `ClientboundBlockEventPacket`.
- `mod-fabric/src/main/java/globe/world/mixin/PlayerListBroadcastMixin.java`
  - Send all packets returned by the new fanout helper.
- `mod-fabric/src/main/java/globe/world/mixin/ContainerOpenersCounterMixin.java`
  - Supplement opener recheck entity candidates with alias-near players.
- `mod-fabric/src/main/resources/globe-world.mixins.json`
  - Register `ContainerOpenersCounterMixin`.
- `docs/mod-mechanics/blocks-and-ticks.md`
  - After implementation, update the world-event paragraph to say block events
    are fanned out to all loaded aliases.
- `docs/vanilla-mechanics/block-entities.md`
  - Add a chest/open-count note if useful after validating the fix.

## Validation

Manual playtest cases:

- Small tile where multiple aliases of the same chest are visible at once:
  open and close the chest; every visible copy should close.
- Open a chest near an X seam, move across the seam while the menu is open, then
  close it; no alias should remain open.
- Repeat near a Z seam and near a corner seam.
- Double chest across or near a tile edge; both halves should animate correctly.
- Two players viewing different aliases of the same canonical chest; opening and
  closing by either player should update both clients' visible aliases.
- Move away while the chest menu is open and wait through at least one opener
  recheck tick; the server should not prematurely close the chest while the menu
  is still open and topologically in range.

Recommended diagnostics while testing:

- Add temporary debug logging when block-event fanout emits more than one alias:
  player, canonical position, source packet position, emitted positions, and
  open count.
- Add temporary debug logging when the container opener mixin supplements a
  player that vanilla's raw `AABB` query missed.

## Regression Risks

- Sending block events to all loaded aliases can make distant visible copies
  animate even when vanilla would have sent only one raw-position event. That is
  intended for Globe's alias illusion, but keep the existing wrapped broadcast
  range gate to avoid unnecessary sends to unrelated players.
- `ContainerOpenersCounter` is shared by chests, trapped chests, barrels, ender
  chests, and similar containers. The alias-aware candidate supplement should be
  conservative and player-only unless another `ContainerUser` implementation is
  found.
- If `ChunkAliasTracker` cleanup is incomplete, fanout could target stale client
  chunks. Dedupe final positions and rely on normal client chunk checks; also
  watch chunk unload/reload cases during validation.

## Done Criteria

- Closing a chest sends close-count block events to every loaded alias for the
  affected canonical chunk.
- Opener rechecks count a player standing at a visible alias of an open
  canonical container.
- The issue is removed from `dev-work.md` or replaced with a link to the
  completed mod-mechanics docs.
- Durable behavior is folded into `docs/mod-mechanics/blocks-and-ticks.md`, and
  this plan is retired or kept only for historical investigation notes.
