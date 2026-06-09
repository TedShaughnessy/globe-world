# Chest Lid Alias Sync

## Status

Implemented. Vanilla chest lid state is synchronized through block events, so
Globe World now fans `ClientboundBlockEventPacket` out to every loaded alias of
the affected canonical chunk. Container opener rechecks are also alias-aware for
server players so a chest opened through a visible alias is not prematurely
counted as closed around its canonical block position.

Durable behavior is documented in
[Blocks And Ticks](../mod-mechanics/blocks-and-ticks.md), with vanilla source
anchors in [Block Entities](../vanilla-mechanics/block-entities.md).

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
- `WorldEventPacketUtil.virtualizeForLoadedAliases(...)` fans out
  `ClientboundBlockEventPacket` to every loaded alias tracked by
  `ChunkAliasTracker`.
- `ChunkHolderMixin` routes chunk-holder block update broadcasts through that
  loaded-alias fanout.

Server-side opener rechecks:

- `ContainerOpenersCounter.getEntitiesWithContainerOpen(...)` builds a raw
  `AABB` around the canonical block-entity position and asks
  `Level.getEntities(...)` for candidates.
- In a tiled world, a player can have the chest menu open while standing near a
  visual alias far from the canonical block position.
- `ContainerOpenersCounterMixin` keeps vanilla's raw candidates, then adds
  server players whose bounding boxes intersect the player-nearest alias of the
  search box and whose open menu still matches the container.

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

## Implementation Notes

### 1. Add Loaded-Alias Fanout For Block Events

`WorldEventPacketUtil` has a loaded-alias path for
`ClientboundBlockEventPacket`.

Shape:

```java
public static List<Packet<?>> virtualizeForLoadedAliases(Packet<?> packet, ServerPlayer viewer)
```

This mirrors `BlockPacketUtil` but handles packets owned by the world-event
broadcast path.

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

The offset helper is currently local to `WorldEventPacketUtil`; factor it only
if duplication with `BlockPacketUtil` starts to grow.

### 2. Route Broadcast Block Events Through The Fanout

`PlayerListBroadcastMixin.broadcastWorldEventWithWrappedDistance(...)` sends all
packets returned by the fanout helper:

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

`ContainerOpenersCounterMixin` wraps
`ContainerOpenersCounter.getEntitiesWithContainerOpen(...)`.

File:

- `mod-fabric/src/main/java/globe/world/mixin/ContainerOpenersCounterMixin.java`

Mixin target:

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

The mixin is registered in `mod-fabric/src/main/resources/globe-world.mixins.json`.

### 4. Keep Interaction And Storage Canonical

Do not change:

- `LevelSetBlockBroadcastMixin` canonicalization for block entity lookup.
- `ServerGamePacketListenerImplMixin` client action canonicalization.
- `PlayerInteractionRangeMixin` wrapped block interaction range.

Those paths already make alias interaction operate on the canonical chest. The
new work should only fix lid/event presentation and opener recheck candidates.

## Implemented Files

- `mod-fabric/src/main/java/globe/world/util/WorldEventPacketUtil.java`
  - Adds loaded-alias fanout for `ClientboundBlockEventPacket`.
- `mod-fabric/src/main/java/globe/world/mixin/PlayerListBroadcastMixin.java`
  - Sends all packets returned by the fanout helper.
- `mod-fabric/src/main/java/globe/world/mixin/ContainerOpenersCounterMixin.java`
  - Supplements opener recheck entity candidates with alias-near players.
- `mod-fabric/src/main/resources/globe-world.mixins.json`
  - Registers `ContainerOpenersCounterMixin`.
- `docs/mod-mechanics/blocks-and-ticks.md`
  - Documents loaded-alias block-event fanout and alias-aware opener rechecks.
- `docs/vanilla-mechanics/block-entities.md`
  - Documents vanilla chest/open-count block events and opener rechecks.

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

- Implemented: closing a chest sends close-count block events to every loaded
  alias for the affected canonical chunk.
- Implemented: opener rechecks count a player standing at a visible alias of an
  open canonical container.
- Not applicable in this tree: no `dev-work.md` entry was found during this
  implementation.
- Done: durable behavior is folded into
  `docs/mod-mechanics/blocks-and-ticks.md`, and this plan is retired or kept
  only for historical investigation notes.
