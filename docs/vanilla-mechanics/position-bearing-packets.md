# Position-Bearing Packets

Minecraft game packets often carry block, chunk, entity, or effect positions.
In Globe World, these packets matter when the server's canonical X/Z differs
from the player-visible alias X/Z.

## Source Search

Checked against Minecraft 26.1.2 local Loom sources.

Useful inventory command:

```bash
for f in $(jar tf .gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-52430b475d/26.1.2/minecraft-common-52430b475d-26.1.2-sources.jar | rg 'net/minecraft/network/protocol/game/Clientbound.*Packet.java$'); do
  if unzip -p .gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-52430b475d/26.1.2/minecraft-common-52430b475d-26.1.2-sources.jar "$f" | rg -q 'BlockPos|ChunkPos|SectionPos|PositionMoveRotation|GlobalPos|Vec3|private final double [xyz]|private final int [xyz]|private final int x;|private final int z;|private final double x;|private final double z;'; then
    echo "$f"
  fi
done
```

This search is a starting point, not a substitute for reading send sites. Some
packets encode position indirectly, and some matching fields are relative
movement, velocity, or client-local UI data rather than world coordinates.

## Packet Inventory

| Packet | Coordinate Payload | Vanilla Send/Application Anchor | Globe Classification |
| --- | --- | --- | --- |
| `ClientboundLevelChunkWithLightPacket` | chunk X/Z | `PlayerChunkSender.sendChunk(...)`; `ClientPacketListener.handleLevelChunkWithLight(...)` | Covered by full chunk relabeling. |
| `ClientboundForgetLevelChunkPacket` | chunk X/Z | `PlayerChunkSender.dropChunk(...)`; `ClientPacketListener.handleForgetLevelChunk(...)` | Covered by alias drop handling. |
| `ClientboundChunksBiomesPacket` | list of `ChunkPos` plus biome payloads | `ChunkMap.resendBiomesForChunks(...)`; `ClientPacketListener.handleChunksBiomes(...)` | Covered by loaded-alias fanout. |
| `ClientboundBlockUpdatePacket` | `BlockPos` | `ChunkHolder.broadcastChanges(...)`, direct server-player sends; `ClientPacketListener.handleBlockUpdate(...)` | Covered by block packet utilities and direct sign-editor handling. |
| `ClientboundSectionBlocksUpdatePacket` | `SectionPos` plus section-relative positions | `ChunkHolder.broadcastChanges(...)`; `ClientPacketListener.handleChunkBlocksUpdate(...)` | Covered by block packet utilities. |
| `ClientboundBlockEntityDataPacket` | `BlockPos` | block entity update packet paths; `ClientPacketListener.handleBlockEntityData(...)` | Covered by block packet utilities. |
| `ClientboundLightUpdatePacket` | chunk X/Z | `ChunkHolder.broadcastChanges(...)`; `ClientPacketListener.handleLightUpdatePacket(...)` | Covered by light alias fanout. |
| `ClientboundSoundPacket` | quantized sound X/Y/Z | `ServerLevel.playSeededSound(...)` through `PlayerList.broadcast(...)`; `ClientPacketListener.handleSoundEvent(...)` | Covered by world-event packet utilities and wrapped broadcast distance. |
| `ClientboundSoundEntityPacket` | entity id, with positional broadcast gate | `ServerLevel.playSeededSound(...)` for entity sounds | Packet stays entity-id based; broadcast distance is wrapped. |
| `ClientboundLevelEventPacket` | `BlockPos` | `ServerLevel.levelEvent(...)`, `globalLevelEvent(...)`; `ClientPacketListener.handleLevelEvent(...)` | Covered by world-event packet utilities. |
| `ClientboundBlockEventPacket` | `BlockPos` | `ServerLevel.runBlockEvents(...)`; `ClientPacketListener.handleBlockEvent(...)` | Covered by world-event packet utilities. |
| `ClientboundBlockDestructionPacket` | `BlockPos` | `ServerLevel.destroyBlockProgress(...)`; `ClientPacketListener.handleBlockDestruction(...)` | Covered by direct per-player send handling. |
| `ClientboundLevelParticlesPacket` | particle X/Y/Z | `ServerLevel.sendParticles(...)`; `ClientPacketListener.handleParticleEvent(...)` | Covered by direct per-player send handling. |
| `ClientboundExplodePacket` | explosion center `Vec3`, optional knockback vector | `ServerLevel.explode(...)`; `ClientPacketListener.handleExplosion(...)` | Center is virtualized; knockback remains relative. |
| `ClientboundAddEntityPacket` | entity X/Y/Z | `ServerEntity.sendPairingData(...)`; `ClientPacketListener.handleAddEntity(...)` | Covered by entity packet utilities. |
| `ClientboundEntityPositionSyncPacket` | absolute entity `PositionMoveRotation` | `ServerEntity`, entity re-sync paths; `ClientPacketListener.handleEntityPositionSync(...)` | Covered by entity packet utilities. |
| `ClientboundTeleportEntityPacket` | absolute or relative entity `PositionMoveRotation` | `ServerEntity.sendChanges(...)`; `ClientPacketListener.handleTeleportEntity(...)` | Covered by entity packet utilities; relative X/Z stay relative. |
| `ClientboundMoveEntityPacket` | relative entity deltas | `ServerEntity.sendChanges(...)`; `ClientPacketListener.handleMoveEntity(...)` | Intentionally unchanged; nearest-alias changes trigger absolute re-sync. |
| `ClientboundDamageEventPacket` | optional damage source `Vec3` | `ServerLevel.broadcastDamageEvent(...)`; `ClientPacketListener.handleDamageEvent(...)` | Covered when a source position is present. |
| `ClientboundMoveVehiclePacket` | vehicle `Vec3` | `ServerGamePacketListenerImpl.handleMoveVehicle(...)`; `ClientPacketListener.handleMoveVehicle(...)` | Covered for direct vehicle correction packets. |
| `ClientboundMoveMinecartPacket` | minecart interpolation step `Vec3` list | tracked entity send path; `ClientPacketListener.handleMinecartAlongTrack(...)` | Covered by entity packet utilities. |
| `ClientboundSetEntityMotionPacket` | velocity `Vec3` | entity motion send paths; `ClientPacketListener.handleSetEntityMotion(...)` | Intentionally unchanged; payload is movement, not a world position. |
| `ClientboundOpenSignEditorPacket` | sign `BlockPos` | `ServerPlayer.openTextEdit(...)`; `ClientPacketListener.handleOpenSignEditor(...)` | Covered with the preceding direct block update. |
| `ClientboundPlayerLookAtPacket` | explicit/fallback X/Y/Z and optional entity target | `ServerPlayer.lookAt(...)`; `ClientPacketListener.handleLookAt(...)` | Covered while preserving entity-target metadata. |
| `ClientboundPlayerPositionPacket` | local player `PositionMoveRotation` | `ServerGamePacketListenerImpl.teleport(...)`; `ClientPacketListener.handleMovePlayer(...)` | Intentionally not globally virtualized. This packet moves the receiving player and participates in teleport ack state. Existing Globe lifecycle hooks canonicalize login, respawn, and wake-up before vanilla sends it. |
| `ClientboundSetDefaultSpawnPositionPacket` | `LevelData.RespawnData` with world spawn position | `PlayerList.sendLevelInfo(...)`, respawn path; `ClientPacketListener.handleSetSpawn(...)` | Intentionally canonical unless testing proves a visible client-side navigation leak. |
| `ClientboundTrackedWaypointPacket` | waypoint position `Vec3i`, `ChunkPos`, azimuth, or empty id | `WaypointTransmitter` connection classes; `ClientPacketListener.handleWaypoint(...)` | Block/chunk positions are covered and resent when the receiver's nearest alias changes; azimuth angles use wrapped direction; empty waypoints are unchanged. |
| `ClientboundMapItemDataPacket` | map-local decoration bytes and color patch | `MapItemSavedData.getUpdatePacket(...)`; `ClientPacketListener.handleMapItemData(...)` | Covered upstream before packet construction for tracked player icons. |
| `ClientboundSetChunkCacheCenterPacket` | chunk X/Z view center | `ChunkMap.applyChunkTrackingView(...)`; `ClientPacketListener.handleSetChunkCacheCenter(...)` | Player-view state, not canonical chunk data. Leave unchanged. |
| `ClientboundBlockChangedAckPacket` | sequence ack | `ServerGamePacketListenerImpl` block prediction ack paths | No world position. |
| Debug and GameTest packets | debug `BlockPos`/`ChunkPos` | debug subscription and GameTest paths | Low priority; classify as intentionally ignored unless gameplay relies on them. |

## Sign Editor Notes

`ServerPlayer.openTextEdit(...)` sends a direct
`ClientboundBlockUpdatePacket` followed by `ClientboundOpenSignEditorPacket`.
When the client saves the editor, `ServerboundSignUpdatePacket` carries the
sign `BlockPos` back to `ServerGamePacketListenerImpl.updateSignText(...)`.
Vanilla checks `level.hasChunkAt(pos)` and fetches `level.getBlockEntity(pos)`
with that packet position before calling `SignBlockEntity.updateSignText(...)`.
While the editor is open, `SignBlockEntity.tick(...)` can also clear the
allowed editor UUID if `Player.isWithinBlockInteractionRange(...)` says the
player is too far from the sign block position.
When opening an existing sign, `SignBlockEntity.isFacingFrontText(...)` derives
the front/back side from the player's X/Z relative to
`SignBlockEntity.getBlockPos()`.

Globe consequence: the outbound editor position must be virtualized to the
visible alias, then the inbound save position must be canonicalized before the
chunk/block-entity lookup. Block interaction range checks also need wrapped
distance so the sign does not revoke edit permission while the player is
standing next to an alias copy. Front/back detection needs the same visible
alias position, otherwise a player can open and save the hidden side of an alias
sign while looking at the other side.

The server's general block-entity lookup must also canonicalize alias positions.
Otherwise `Level.getBlockEntity(aliasPos)` can fetch the canonical chunk but ask
that chunk for an alias-keyed block entity, producing a transient sign that
edits in memory but does not represent durable canonical chunk state.

## Player Position Packet Notes

`ServerGamePacketListenerImpl.teleport(PositionMoveRotation, Set<Relative>)`
updates the server player with `player.teleportSetPosition(...)`, stores
`awaitingPositionFromClient = player.position()`, then sends
`ClientboundPlayerPositionPacket.of(...)`.

`ClientPacketListener.handleMovePlayer(...)` applies the packet to the local
player, sends `ServerboundAcceptTeleportationPacket`, and immediately reports
the resulting position back to the server.

Globe consequence:

- Login, respawn, and bed wake-up should canonicalize the server player before
  vanilla calls `teleport(...)`.
- Normal in-session teleports should generally stay in the coordinate space the
  server player is currently using.
- A broad per-viewer rewrite would desynchronize teleport acknowledgement unless
  the server player state and awaiting position were changed in the same
  semantic operation.

## Spawn Packet Notes

`ClientboundSetDefaultSpawnPositionPacket` stores `LevelData.RespawnData` on the
client level. It represents a dimension-level respawn anchor, not an immediate
view-relative effect. Globe currently leaves it canonical and handles visible
spawn/navigation behavior through more specific compass and player lifecycle
hooks.

## Waypoint Notes

Waypoints have three relevant forms:

- block waypoints: world `Vec3i`
- chunk waypoints: `ChunkPos`
- azimuth waypoints: angle only

The locator-bar connection choice is distance-sensitive. For tiled dimensions,
connection range, "really far" classification, and chunk visibility need wrapped
logic before the packet is built. Once a block or chunk connection exists, the
packet position should be sent in the receiver's nearest visible alias. Because
the receiver can cross an alias threshold while the source remains in the same
raw block or chunk, existing block/chunk connections also need to resend when
the receiver-nearest alias changes. Azimuth packets still carry an angle, but
the angle should be computed through the shortest wrapped path.
