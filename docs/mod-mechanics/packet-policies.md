# Packet Policies

## What

Globe World keeps a code-side packet policy table for the Minecraft 26.1.2
position-bearing packets it currently virtualizes or deliberately leaves alone.
The table is audit documentation, not a packet dispatcher: packet utilities and
mixins still perform the handwritten copies that preserve each packet's vanilla
semantics.

## Policy Categories

| Category | Meaning |
| --- | --- |
| `CHUNK_OWNER` | Canonical chunk data is sent with alias-facing chunk coordinates. |
| `BLOCK_NEAREST_ALIAS` | A block position is moved to the nearest alias for one receiver. |
| `BLOCK_LOADED_ALIAS_FANOUT` | Canonical block/chunk data is copied to every loaded visible alias, with nearest-alias fallback. |
| `WORLD_EVENT_NEAREST_ALIAS` | A sound, particle, break, event, or explosion position is moved to the nearest alias for one receiver. |
| `WORLD_EVENT_LOADED_ALIAS_FANOUT` | A world event is copied to loaded aliases of its canonical owner. |
| `ENTITY_NEAREST_ALIAS` | Absolute entity positions are moved to the receiver's nearest alias. |
| `NAVIGATION_UI_NEAREST_ALIAS` | UI/navigation packets such as look-at, signs, waypoints, and maps use the nearest visible alias. |
| `RELATIVE_OR_NON_POSITION_NOOP` | Packet data is relative to another identity or otherwise keeps vanilla coordinates. |
| `INTENTIONALLY_UNSUPPORTED` | Known packet type has no Globe World support yet. |

## Minecraft 26.1.2 Table

| Packet | Policy | Owner | Semantics |
| --- | --- | --- | --- |
| `ClientboundLevelChunkWithLightPacket` | `CHUNK_OWNER` | `PlayerChunkSenderMixin` | Send canonical chunk data with the raw alias chunk header; record the alias in `ChunkAliasTracker`. |
| `ClientboundForgetLevelChunkPacket` | `CHUNK_OWNER` | `PlayerChunkSenderMixin` | Forget the raw alias chunk and remove that alias visibility record. |
| `ClientboundBlockUpdatePacket` | `BLOCK_LOADED_ALIAS_FANOUT` | `BlockPacketUtil`, `ServerPlayerInteractionPacketMixin` | Fan out block updates to loaded aliases; targeted sign-edit support uses nearest-alias fallback. |
| `ClientboundBlockEntityDataPacket` | `BLOCK_LOADED_ALIAS_FANOUT` | `BlockPacketUtil` | Copy block-entity update position to every loaded alias. |
| `ClientboundSectionBlocksUpdatePacket` | `BLOCK_LOADED_ALIAS_FANOUT` | `BlockPacketUtil` | Relabel section headers while preserving packed local offsets and states. |
| `ClientboundLightUpdatePacket` | `BLOCK_LOADED_ALIAS_FANOUT` | `BlockPacketUtil` | Relabel light chunk headers to loaded aliases, or nearest alias if no loaded record exists. |
| `ClientboundChunksBiomesPacket` | `BLOCK_LOADED_ALIAS_FANOUT` | `ChunkPacketUtil` | Resend biome data for loaded aliases, with nearest-alias fallback. |
| `ClientboundSoundPacket` | `WORLD_EVENT_NEAREST_ALIAS` | `WorldEventPacketUtil` | Move absolute sound coordinates to the receiver-nearest alias. |
| `ClientboundSoundEntityPacket` | `RELATIVE_OR_NON_POSITION_NOOP` | `WorldEventPacketUtil` | Recognized as handled; entity-attached sound has no absolute coordinate relabeling. |
| `ClientboundLevelEventPacket` | `WORLD_EVENT_NEAREST_ALIAS` | `WorldEventPacketUtil`, `ServerLevelWorldEventMixin` | Move block-event coordinates to the receiver-nearest alias. |
| `ClientboundBlockEventPacket` | `WORLD_EVENT_LOADED_ALIAS_FANOUT` | `WorldEventPacketUtil` | Fan out to loaded aliases, falling back to nearest-alias relabeling. |
| `ClientboundBlockDestructionPacket` | `WORLD_EVENT_NEAREST_ALIAS` | `WorldEventPacketUtil`, `ServerLevelWorldEventMixin` | Move block break progress to the receiver-nearest alias. |
| `ClientboundLevelParticlesPacket` | `WORLD_EVENT_NEAREST_ALIAS` | `WorldEventPacketUtil` | Move particle origins to the receiver-nearest alias. |
| `ClientboundExplodePacket` | `WORLD_EVENT_NEAREST_ALIAS` | `WorldEventPacketUtil` | Move explosion center; keep explosion payload details unchanged. |
| `ClientboundBundlePacket` | `ENTITY_NEAREST_ALIAS` | `EntityPacketUtil` | Recursively virtualize handled sub-packets. |
| `ClientboundAddEntityPacket` | `ENTITY_NEAREST_ALIAS` | `EntityPacketUtil` | Move spawn coordinates to the receiver-nearest alias. |
| `ClientboundEntityPositionSyncPacket` | `ENTITY_NEAREST_ALIAS` | `EntityPacketUtil`, `ChunkMapTrackedEntityMixin` | Move absolute sync coordinates to the receiver-nearest alias. |
| `ClientboundTeleportEntityPacket` | `ENTITY_NEAREST_ALIAS` | `EntityPacketUtil`, `ChunkMapTrackedEntityMixin` | Virtualize absolute axes while preserving relative X/Z flags. |
| `ClientboundDamageEventPacket` | `ENTITY_NEAREST_ALIAS` | `EntityPacketUtil` | Move optional damage source position to the receiver-nearest alias. |
| `ClientboundMoveVehiclePacket` | `ENTITY_NEAREST_ALIAS` | `EntityPacketUtil` | Move vehicle absolute correction position. |
| `ClientboundMoveMinecartPacket` | `ENTITY_NEAREST_ALIAS` | `EntityPacketUtil` | Move minecart interpolation step positions. |
| `ClientboundMoveEntityPacket` | `RELATIVE_OR_NON_POSITION_NOOP` | `ChunkMapTrackedEntityMixin` | Keep relative movement vanilla; crossing tile thresholds sends an absolute sync. |
| `ClientboundPlayerLookAtPacket` | `NAVIGATION_UI_NEAREST_ALIAS` | `ServerPlayerInteractionPacketMixin` | Move look-at target coordinates to the player's nearest alias. |
| `ClientboundOpenSignEditorPacket` | `NAVIGATION_UI_NEAREST_ALIAS` | `ServerPlayerInteractionPacketMixin` | Move sign editor block position to the player's nearest alias. |
| `ClientboundTrackedWaypointPacket` | `NAVIGATION_UI_NEAREST_ALIAS` | `WaypointPacketUtil`, waypoint mixins | Use receiver-nearest alias and wrapped range/visibility checks for block, chunk, and azimuth waypoints. |
| `ClientboundMapItemDataPacket` | `NAVIGATION_UI_NEAREST_ALIAS` | `MapItemMixin`, `MapItemSavedDataMixin` | Map source coordinates and player decorations use nearest map aliases before packet data is built. |

## Code Anchors

- `PacketVirtualizationPolicies` is the code-side checklist.
- `BlockPacketUtil`, `ChunkPacketUtil`, `WorldEventPacketUtil`,
  `EntityPacketUtil`, and `WaypointPacketUtil` hold the main handwritten
  transformers.
- `PlayerChunkSenderMixin`, `ChunkMapTrackedEntityMixin`,
  `ServerPlayerInteractionPacketMixin`, `MapItemMixin`, and
  `MapItemSavedDataMixin` cover packet-adjacent vanilla hooks.

## Update Checklist

When updating Minecraft, audit each row against the new packet class and field
shape, then update both `PacketVirtualizationPolicies` and this table. New
absolute position-bearing packets should be added as a policy before their
transformer is implemented.
