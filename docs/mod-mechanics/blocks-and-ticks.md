# Blocks And Ticks

## What

Block reads and writes through aliases canonicalize X/Z before touching mutable
state. Viewer-facing block packets are then copied or relabeled back to the
aliases each player has loaded.

Block and fluid tick lanes should run side effects once per canonical position,
even when multiple aliases of the same canonical chunk are loaded.

## Why

Alias edits must mutate the same block state as canonical edits. Random ticks,
scheduled ticks, and neighbor updates are side-effecting systems; letting aliases
run them independently would duplicate growth, fluid spread, fire, redstone, and
other mutable behavior.

## Implementation

Block/chunk access is wrapped into canonical coordinates before mutation.
Reentrant or skipped block-update notification paths send the actual post-update
state so the client still receives redstone shape changes such as dot-to-line
updates.

`BlockPacketUtil` virtualizes single-block updates, multi-block section updates,
block-entity data packets, and incremental light update packets. When a player
has multiple loaded aliases for the same canonical chunk, it emits one packet
per alias with positions or chunk coordinates offset into that alias.

Random block ticks are canonicalized and deduped during
`ChunkMap.forEachBlockTickingChunk`. Globe snapshots the ticking chunk keys
before running callbacks so side effects during block ticks cannot mutate the
distance-manager map while vanilla is still iterating it. Scheduled block/fluid
ticks canonicalize positions in `LevelTicks.schedule`, `hasScheduledTick`, and
`willTickThisTick`.

Lodestone compass tracking validates the lodestone point of interest at the
canonical target position in tiled dimensions. This keeps compasses bound to an
alias lodestone from being cleared just because the stored `GlobalPos` is
outside the canonical tile.

## Key Files

- `src/main/java/globe/world/util/BlockPacketUtil.java`
- `src/main/java/globe/world/util/ChunkAliasTracker.java`
- `src/main/java/globe/world/util/CoordUtil.java`
- `src/main/java/globe/world/mixin/ChunkMapBlockTickingMixin.java`
- `src/main/java/globe/world/mixin/LevelSetBlockBroadcastMixin.java`
- `src/main/java/globe/world/mixin/LodestoneTrackerMixin.java`
- `src/main/java/globe/world/mixin/ServerPlayerGameModeMixin.java`
- `src/main/java/globe/world/mixin/BulkSectionAccessMixin.java`
- `src/main/java/globe/world/mixin/ChunkMapPlayerProviderMixin.java`
- `src/main/java/globe/world/mixin/ChunkMapRandomTickMixin.java`
- `src/main/java/globe/world/mixin/LevelTicksMixin.java`
- `src/main/java/globe/world/mixin/ServerLevelTicksDimensionMixin.java`
- `src/main/java/globe/world/mixin/ClientboundBlockEntityDataPacketAccessor.java`
- `src/main/java/globe/world/mixin/ClientboundLightUpdatePacketAccessor.java`
- `src/main/java/globe/world/mixin/ClientboundSectionBlocksUpdatePacketAccessor.java`

## Related Vanilla Mechanics

- [Vanilla block updates](../../vanilla-mechanics/block-updates.md)
- [Vanilla scheduled ticks](../../vanilla-mechanics/scheduled-ticks.md)
- [Vanilla random ticks](../../vanilla-mechanics/random-ticks.md)
- [Vanilla fluids](../../vanilla-mechanics/fluids.md)
- [Vanilla block entities](../../vanilla-mechanics/block-entities.md)

## Open Audits

- Block entity persistence and ticking should be audited separately from update
  packet fanout.
- Scheduled tick clone/copy operations and cross-edge simulation reach need
  edge-case testing.
- Redstone, pistons, observers, doors, and similar neighbor-sensitive blocks
  need focused cross-edge testing.
- Server-side light propagation across canonical tile edges needs investigation
  if visible seams remain after incremental light packet fanout.
