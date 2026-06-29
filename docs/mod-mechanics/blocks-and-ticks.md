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
Runtime block/chunk helper paths resolve those owners through
`TopologyContext`, which gives packet fanout, block mutation, chunk lookup, and
alias lifecycle code the same dimension-aware names for canonical owners,
viewer-facing positions, loaded aliases, and wrapped chunk distances.
Reentrant or skipped block-update notification paths send the actual post-update
state so the client still receives redstone shape changes such as dot-to-line
updates.

Server block-entity lookup, removal, and dirty marking also canonicalize X/Z.
This prevents alias interactions from creating transient alias-position block
entities inside canonical chunks. Those transient entries can appear editable in
memory, but their update packets do not fan out correctly and their coordinates
do not belong to the canonical chunk on reload.

Client block actions that originate from alias coordinates are allowed to mutate
the canonical block only while the matching canonical chunk is in block-ticking
range. `TopologyContext.shouldAllowAliasMutation` owns this policy; block
breaking, item use on blocks, and sign text saves use this guard.
Crop survival checks read canonical server light before accepting placement.
Block state lookup for alias crop placement already resolves through canonical
chunks, but vanilla `LevelLightEngine` indexes raw light sections; using the
canonical crop position keeps generated terrain's old alias light data from
making valid air-above-farmland positions look too dark.
Sign text packets also canonicalize the client-sent sign position before vanilla
checks chunk availability and fetches the `SignBlockEntity`, so editing a sign
through a visible alias writes the canonical sign text. Player block-interaction
range checks use the nearest wrapped copy of the target block, which keeps
vanilla's sign edit permission from expiring just because the editable sign is
stored at its canonical coordinates. Sign front/back detection also compares the
player against the sign's nearest virtual alias, so editing an existing alias
sign opens the same side the player is actually looking at.

`BlockPacketUtil` virtualizes single-block updates, multi-block section updates,
block-entity data packets, and incremental light update packets. When a player
has multiple loaded aliases for the same canonical chunk, it emits one packet
per alias with positions or chunk coordinates offset into that alias.

`WorldEventPacketUtil` virtualizes world-event and cosmetic packets whose
positions are not sent through `ChunkHolder`: positional sounds, level events,
block events, block destruction progress, particles, and explosion centers.
Block events are fanned out to every loaded alias of the affected canonical
chunk because container lids and similar block-entity presentations mutate
client-local state from those events rather than from ordinary block updates.
`PlayerListBroadcastMixin` wraps vanilla's positional broadcast path so players
near an alias receive sounds and events using wrapped distance checks.
`ServerLevelWorldEventMixin` handles the custom per-player send paths for block
destruction, particles, explosions, and global level events.

Container opener rechecks keep the server open count canonical, but add
alias-near players to vanilla's candidate list. This prevents chests opened
through a visible alias from being counted as closed during the periodic
`ContainerOpenersCounter` search around the canonical block position.

Random block ticks are canonicalized and deduped during
`ChunkMap.forEachBlockTickingChunk`. Globe snapshots the ticking chunk keys
before running callbacks so side effects during block ticks cannot mutate the
distance-manager map while vanilla is still iterating it. Scheduled block/fluid
ticks canonicalize positions in `LevelTicks.schedule`, `hasScheduledTick`, and
`willTickThisTick`. These runtime tick helpers now enter wrapping through
`TopologyContext`.

Vanilla fire spread and burnout run from scheduled fire block ticks, but
Minecraft 26.1.2 also gates that logic on whether a non-spectator player is
close enough to the fire block. `ChunkMapPlayerDistanceMixin` wraps that block
proximity distance so a player standing in a visible alias counts as close to
the canonical fire position.

Other block and block-entity systems use generic player proximity helpers.
Mob spawners call `EntityGetter.hasNearbyAlivePlayer`, enchanting tables and
creaking hearts call `EntityGetter.getNearestPlayer`, and trial spawners and
vaults use `PlayerDetector`. Globe wraps these checks so alias players count as
near the canonical block entity. Trial/vault line-of-sight checks raycast from
the detector's visible alias toward the player instead of from canonical storage
coordinates.

Additional vanilla proximity predicates that bypass those generic helpers are
wrapped where they affect block-driven gameplay: beacon/conduit effect radius,
warden warning players from sculk shriekers, beehive anger range, and lightning
strike advancement range.

Entity-sensitive block triggers now use audited visible-frame query hooks rather
than a global `EntityGetter` replacement. Pressure plates, weighted pressure
plates, arrow-activated buttons, detector rails, tripwire, hopper and
hopper-minecart item/container pickup, chest cat blocking, shulker lid
obstruction and lid pushing, and moving piston displacement query
canonical entities through wrapped alias boxes and dedupe by real entity
identity. The per-caller policy and regression cases live in
[Entity Query Caller Matrix](entity-query-caller-matrix.md).

Lodestone compass tracking validates the lodestone point of interest at the
canonical target position in tiled dimensions. This keeps compasses bound to an
alias lodestone from being cleared just because the stored `GlobalPos` is
outside the canonical tile. On the client, lodestone, recovery, and world-spawn
compass targets choose one whole-position alias nearest the item owner, so hex
needles can point across oblique seams without mixing independent X/Z aliases.

## Key Files

- `mod-fabric/src/main/java/globe/world/util/BlockPacketUtil.java`
- `mod-fabric/src/main/java/globe/world/util/WorldEventPacketUtil.java`
- `mod-fabric/src/main/java/globe/world/util/ClientActionDiagnostics.java`
- `mod-fabric/src/main/java/globe/world/util/ChunkAliasTracker.java`
- `mod-fabric/src/main/java/globe/world/topology/TopologyContext.java`
- `mod-fabric/src/main/java/globe/world/util/CoordUtil.java`
- `mod-fabric/src/main/java/globe/world/mixin/ChunkMapBlockTickingMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ChunkMapPlayerDistanceMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/EntityGetterPlayerDistanceMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/MobEffectUtilMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/LevelSetBlockBroadcastMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/PlayerDetectorMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/PressurePlateBlockEntityQueryMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/WeightedPressurePlateBlockEntityQueryMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ButtonBlockEntityQueryMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/CropBlockLightMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/DetectorRailBlockEntityQueryMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/TripWireBlockEntityQueryMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/HopperBlockEntityQueryMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/MinecartHopperEntityQueryMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ChestBlockCatQueryMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ShulkerBoxBlockEntityQueryMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ShulkerBoxBlockEntityCollisionMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/PistonMovingBlockEntityQueryMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/WardenSpawnTrackerMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/BeehiveBlockEntityMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/LightningBoltMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ContainerOpenersCounterMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/PlayerListBroadcastMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ServerLevelWorldEventMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/LodestoneTrackerMixin.java`
- `mod-fabric/src/client/java/globe/world/client/mixin/CompassAngleStateMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/PlayerInteractionRangeMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ServerGamePacketListenerImplMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/SignBlockEntityFacingMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ServerPlayerGameModeMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/BulkSectionAccessMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ChunkMapPlayerProviderMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ChunkMapRandomTickMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/LevelTicksMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ServerLevelTicksDimensionMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ClientboundBlockEntityDataPacketAccessor.java`
- `mod-fabric/src/main/java/globe/world/mixin/ClientboundLightUpdatePacketAccessor.java`
- `mod-fabric/src/main/java/globe/world/mixin/ClientboundSectionBlocksUpdatePacketAccessor.java`

## Related Vanilla Mechanics

- [Vanilla block updates](../vanilla-mechanics/block-updates.md)
- [Vanilla scheduled ticks](../vanilla-mechanics/scheduled-ticks.md)
- [Vanilla random ticks](../vanilla-mechanics/random-ticks.md)
- [Vanilla fluids](../vanilla-mechanics/fluids.md)
- [Vanilla block entities](../vanilla-mechanics/block-entities.md)

## Open Audits

- Block entity persistence and ticking should be audited separately from update
  packet fanout.
- Scheduled tick clone/copy operations and cross-edge simulation reach need
  edge-case testing.
- Redstone, observers, doors, and similar neighbor-sensitive blocks need focused
  cross-edge testing.
- Piston entity displacement is implemented for visible alias boxes, but needs
  focused manual testing with slime, honey, passengers, and entities overlapping
  both canonical and alias frames.
- Server-side light propagation across canonical tile edges needs investigation
  if visible seams remain after incremental light packet fanout.
