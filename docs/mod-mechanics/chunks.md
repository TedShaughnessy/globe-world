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

Server chunk lookup wraps requested X/Z to canonical chunk coordinates before
loading or returning a chunk. Alias chunk lifecycle events keep the matching
canonical chunk available through ref-counted mod tickets.

Outbound full chunk packets are built from canonical chunk data and then
relabeled to the alias chunk position on the wire. Forget/unload packets keep
the raw alias position because that is the client-visible chunk being removed.

The server tracks which alias chunks each player has loaded for a canonical
chunk in that player's current dimension. Later block, section, block-entity,
and incremental light updates fan out to every visible alias, not just the
nearest virtual copy.

Alias tracking is client-visibility bookkeeping. It is populated when chunk
packets are sent, trimmed when chunk drop packets are sent, and force-cleared
for player disconnect, respawn, dimension transfer, level close, and server
stop. These records are not canonical world authority, are not saved, and are
safe to discard because new chunk sends repopulate them.

## Key Files

- `src/main/java/globe/world/GlobeChunkPacket.java`
- `src/main/java/globe/world/util/CanonicalChunkTickets.java`
- `src/main/java/globe/world/util/ChunkAliasTracker.java`
- `src/main/java/globe/world/util/CoordUtil.java`
- `src/main/java/globe/world/mixin/ServerChunkCacheMixin.java`
- `src/main/java/globe/world/mixin/PlayerChunkSenderMixin.java`
- `src/main/java/globe/world/mixin/PlayerListCanonicalPositionMixin.java`
- `src/main/java/globe/world/mixin/ServerPlayerCanonicalPositionMixin.java`
- `src/main/java/globe/world/mixin/ClientboundLevelChunkWithLightMixin.java`
- `src/main/java/globe/world/mixin/ChunkHolderMixin.java`
- `src/main/java/globe/world/mixin/ChunkMapCanonicalTicketMixin.java`
- `src/main/java/globe/world/mixin/MinecraftServerMixin.java`

## Related Vanilla Mechanics

- [Vanilla chunk loading](../../vanilla-mechanics/chunk-loading.md)
- [Vanilla client world](../../vanilla-mechanics/client-world.md)
- [Client packet and render mechanics](client.md)

## Open Audits

- Confirm alias ticket cleanup remains complete during all level shutdown paths.
- Keep chunk load diagnostics limited to targeted warning/debug flows.
