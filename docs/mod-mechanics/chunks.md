# Chunks

## What

Only canonical chunks store mutable chunk state. Non-canonical chunks are virtual
views of canonical chunks, and server packets are relabeled so clients can render
those views at ordinary raw chunk coordinates.

## Why

Vanilla asks for chunks by raw coordinate. Without canonical lookup, every alias
would become an independent chunk with separate blocks, entities, ticks, and
lighting. Without packet relabeling, the client would only see the canonical
copy and would unload or ignore visible aliases.

## Implementation

Server chunk lookup resolves requested chunk coordinates to canonical chunk
owners before loading or returning a chunk. Square worlds wrap X/Z
independently; experimental hex worlds resolve ownership through the discrete
hex chunk mask and lattice in `TileGeometry`. Runtime lookup, packet relabeling,
alias tracking, and canonical alias tickets resolve those owners through
`TopologyContext`.
Alias chunk lifecycle events keep the matching canonical chunk available through
ref-counted mod tickets.

Tiled dimensions cap the effective server simulation distance before
`ServerChunkCache` forwards it to vanilla's `DistanceManager`. The saved server
setting and `PlayerList` value stay unchanged, while the dimension-local value
becomes `min(configured, max(2, ceil(tileSizeChunks / 2)))`. Untiled
dimensions use the configured value unchanged. Vanilla's simulation tracker
spreads across all eight neighboring chunks with the same cost, so this radius
is enough to reach every wrapped chunk in the canonical tile, including corners,
when the configured simulation distance is at least that large. In hex mode the
same cap is conservative rather than shape-exact. Globe World does not rewrite
login or simulation-distance update packets, so clients still receive vanilla's
configured global simulation distance; v1 keeps client-side simulation range
uncapped to avoid per-dimension packet complexity.

Outbound full chunk packets are built from canonical chunk data and then
relabeled to the alias chunk position on the wire. Forget/unload packets keep
the raw alias position because that is the client-visible chunk being removed.

The server tracks which alias chunks each player has loaded for a canonical
chunk in that player's current dimension. Later block, section, block-entity,
and incremental light updates fan out to every visible alias, not just the
nearest virtual copy.

Biome resend packets also use loaded alias tracking. When vanilla resends biome
data for canonical chunks, `ChunkPacketUtil` copies each biome payload under
the alias chunk positions that the receiving player has loaded. If an alias
record is missing, it falls back to the nearest virtual chunk for that player.

Alias tracking is client-visibility bookkeeping. It is populated when chunk
packets are sent, trimmed when chunk drop packets are sent, and force-cleared
for player disconnect, respawn, dimension transfer, level close, and server
stop. These records are not canonical world authority, are not saved, and are
safe to discard because new chunk sends repopulate them.

## Key Files

- `mod-fabric/src/main/java/globe/world/GlobeChunkPacket.java`
- `mod-fabric/src/main/java/globe/world/util/CanonicalChunkTickets.java`
- `mod-fabric/src/main/java/globe/world/util/ChunkAliasTracker.java`
- `mod-fabric/src/main/java/globe/world/util/ChunkPacketUtil.java`
- `mod-fabric/src/main/java/globe/world/topology/TileGeometry.java`
- `mod-fabric/src/main/java/globe/world/topology/HexTileGeometry.java`
- `mod-fabric/src/main/java/globe/world/topology/TopologyContext.java`
- `mod-fabric/src/main/java/globe/world/util/CoordUtil.java`
- `mod-fabric/src/main/java/globe/world/util/GlobeDistanceCaps.java`
- `mod-fabric/src/main/java/globe/world/mixin/ChunkMapBiomeResendMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ServerChunkCacheMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/PlayerChunkSenderMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/PlayerListCanonicalPositionMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ServerPlayerCanonicalPositionMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ClientboundLevelChunkWithLightMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ChunkHolderMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ChunkMapCanonicalTicketMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/MinecraftServerMixin.java`

## Related Vanilla Mechanics

- [Vanilla chunk loading](../vanilla-mechanics/chunk-loading.md)
- [Vanilla client world](../vanilla-mechanics/client-world.md)
- [Client packet and render mechanics](client.md)

## Open Audits

- Confirm alias ticket cleanup remains complete during all level shutdown paths.
- Keep chunk load diagnostics limited to targeted warning/debug flows.
- Prevent tiny-tile raw alias demand from producing large numbers of persisted
  vanilla chunk records. An 8-chunk hex has only 48 canonical owners, but manual
  testing with a render distance larger than the tile showed raw alias chunk
  holders creating substantial generation, save, and streaming overhead.
