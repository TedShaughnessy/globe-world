# Packet Virtualization Audit Plan

Status: complete. The Minecraft 26.1.2 position-bearing clientbound packet
audit is finished. High and medium priority packets are either virtualized,
fanned out to loaded aliases, covered upstream before packet construction, or
explicitly classified as intentionally unchanged. Player-position packets are
not broadly virtualized because they are tied to vanilla teleport
acknowledgement state.

## Problem

Globe World relies on server packets being relabeled from canonical X/Z to the
player-visible alias X/Z. Before this audit, the implementation covered the
major chunk, block, light, block-entity, and entity tracking packets, but
vanilla still sent other position-bearing packets through direct player-list,
server-level, player, and entity-tracking paths.

Any missed packet can leak canonical coordinates to the client. Depending on
the packet, that can mean missed sends near tile edges, sounds or particles at
the wrong copy, stale biome data in aliases, break animations on the wrong
block, or command/interaction visuals that point across the whole tile instead
of across the wrapped path.

## Final Coverage

Implemented behavior:

- `ClientboundLevelChunkWithLightPacket`: relabeled by
  `PlayerChunkSenderMixin` and `ClientboundLevelChunkWithLightMixin`.
- `ClientboundForgetLevelChunkPacket`: sent at the alias chunk position and used
  to remove records from `ChunkAliasTracker`.
- `ClientboundBlockUpdatePacket`: fanned out to loaded aliases by
  `BlockPacketUtil` through `ChunkHolderMixin`.
- `ClientboundSectionBlocksUpdatePacket`: fanned out to loaded aliases by
  `BlockPacketUtil` through `ChunkHolderMixin`.
- `ClientboundBlockEntityDataPacket`: fanned out to loaded aliases by
  `BlockPacketUtil` through `ChunkHolderMixin`.
- `ClientboundLightUpdatePacket`: fanned out to loaded aliases by
  `BlockPacketUtil` through `ChunkHolderMixin`.
- `ClientboundAddEntityPacket`, `ClientboundEntityPositionSyncPacket`, and
  `ClientboundTeleportEntityPacket`: virtualized per viewer by
  `EntityPacketUtil`.
- `ClientboundBundlePacket`: recursively virtualized by `EntityPacketUtil` for
  covered entity sub-packets.
- Relative entity movement packets are intentionally left relative. When the
  nearest visible entity alias changes, `ChunkMapTrackedEntityMixin` sends an
  absolute position sync.
- Filled map pixels and player decorations are corrected before
  `ClientboundMapItemDataPacket` is built, so the packet itself is not the
  wrapping point for map player icons.
- `ClientboundSoundPacket`, `ClientboundSoundEntityPacket`,
  `ClientboundLevelEventPacket`, `ClientboundBlockEventPacket`,
  `ClientboundBlockDestructionPacket`, `ClientboundLevelParticlesPacket`, and
  `ClientboundExplodePacket` are handled by `WorldEventPacketUtil`,
  `PlayerListBroadcastMixin`, and `ServerLevelWorldEventMixin`.
- `ClientboundChunksBiomesPacket` is fanned out to loaded aliases by
  `ChunkPacketUtil` through `ChunkMapBiomeResendMixin`.
- `ClientboundDamageEventPacket`, `ClientboundMoveVehiclePacket`, and
  `ClientboundMoveMinecartPacket` are copied by `EntityPacketUtil`. Vehicle
  correction packets sent directly from `ServerGamePacketListenerImpl` are
  routed through the same helper.
- Direct `ServerPlayer.openTextEdit(...)` sends for
  `ClientboundBlockUpdatePacket` and `ClientboundOpenSignEditorPacket` are
  virtualized by `ServerPlayerInteractionPacketMixin`.
- Direct `ServerPlayer.lookAt(...)` sends for `ClientboundPlayerLookAtPacket`
  are virtualized by `ServerPlayerInteractionPacketMixin` and
  `ClientboundPlayerLookAtPacketAccessor`.
- Entity waypoint connections use wrapped distance, nearest visible chunk
  checks, and wrapped azimuth direction through `LivingEntityWaypointMixin` and
  the waypoint connection mixins. Block/chunk waypoint packets send alias
  positions; azimuth packets remain angle-based but the angle is computed
  through the shortest wrapped path.

## Source Snapshot

Checked against local Loom sources for Minecraft 26.1.2:

- Clientbound game packet classes live under
  `net/minecraft/network/protocol/game`.
- `ServerLevel.playSeededSound(...)` constructs `ClientboundSoundPacket` and
  sends it through `PlayerList.broadcast(...)`.
- `ServerLevel.levelEvent(...)` constructs `ClientboundLevelEventPacket` and
  sends it through `PlayerList.broadcast(...)`.
- `ServerLevel.globalLevelEvent(...)` constructs a per-player
  `ClientboundLevelEventPacket` at a computed sound position.
- `ServerLevel.runBlockEvents(...)` constructs `ClientboundBlockEventPacket`
  and sends it through `PlayerList.broadcast(...)`.
- `ServerLevel.destroyBlockProgress(...)` constructs
  `ClientboundBlockDestructionPacket` and does its own distance check.
- `ServerLevel.sendParticles(...)` constructs
  `ClientboundLevelParticlesPacket` and uses a per-player distance check.
- `ServerLevel.explode(...)` constructs `ClientboundExplodePacket` and does its
  own per-player distance check.
- `ChunkMap.resendBiomesForChunks(...)` constructs
  `ClientboundChunksBiomesPacket` and sends one packet per player.
- `ServerPlayer.openTextEdit(...)` sends `ClientboundOpenSignEditorPacket`.
- `ServerPlayer.lookAt(...)` sends `ClientboundPlayerLookAtPacket`.
- `ServerGamePacketListenerImpl.teleport(...)` sends
  `ClientboundPlayerPositionPacket`.
- `PlayerList` sends `ClientboundSetDefaultSpawnPositionPacket` on login and
  respawn.

## Packet Triage

| Packet | Coordinate Type | Status | Disposition |
| --- | --- | --- | --- |
| `ClientboundLevelChunkWithLightPacket` | `ChunkPos` | covered | Keep as implemented. |
| `ClientboundForgetLevelChunkPacket` | `ChunkPos` | covered | Keep as implemented. |
| `ClientboundBlockUpdatePacket` | `BlockPos` | covered | Keep as implemented. |
| `ClientboundSectionBlocksUpdatePacket` | `SectionPos` | covered | Keep as implemented. |
| `ClientboundBlockEntityDataPacket` | `BlockPos` | covered | Keep as implemented. |
| `ClientboundLightUpdatePacket` | chunk X/Z | covered | Keep as implemented; validate manually at seams. |
| `ClientboundAddEntityPacket` | entity X/Y/Z | covered | Keep as implemented. |
| `ClientboundEntityPositionSyncPacket` | entity X/Y/Z | covered | Keep as implemented. |
| `ClientboundTeleportEntityPacket` | entity X/Y/Z plus relatives | covered | Keep as implemented. |
| `ClientboundMoveEntityPacket` | relative deltas | safe with current rebase sync | Recheck only if entity alias snapping regresses. |
| `ClientboundMapItemDataPacket` | map-local decoration bytes | covered upstream | Map packet data is corrected before packet construction. |
| `ClientboundSoundPacket` | quantized sound X/Y/Z | covered | Covered by world-event packet utilities. |
| `ClientboundSoundEntityPacket` | entity id, positional send gate | covered | Send-distance wrapping is covered; packet stays unchanged. |
| `ClientboundLevelEventPacket` | `BlockPos` | covered | Covered by world-event packet utilities. |
| `ClientboundBlockEventPacket` | `BlockPos` | covered | Covered by world-event packet utilities. |
| `ClientboundBlockDestructionPacket` | `BlockPos` | covered | Covered by direct per-player send handling. |
| `ClientboundLevelParticlesPacket` | particle X/Y/Z | covered | Covered by direct per-player send handling. |
| `ClientboundExplodePacket` | center `Vec3` | covered | Center is virtualized; knockback remains relative. |
| `ClientboundChunksBiomesPacket` | list of `ChunkPos` | covered | Covered by loaded-alias fanout. |
| `ClientboundDamageEventPacket` | optional source `Vec3` | covered | Covered by entity packet utilities when a source position is present. |
| `ClientboundMoveVehiclePacket` | vehicle `Vec3` | covered | Covered by entity packet utilities and direct vehicle correction handling. |
| `ClientboundMoveMinecartPacket` | minecart step `Vec3` list | covered | Covered by entity packet utilities. |
| `ClientboundSetEntityMotionPacket` | velocity `Vec3` | intentionally unchanged | Movement vector is entity velocity, not a world position. |
| `ClientboundOpenSignEditorPacket` | `BlockPos` | covered | Direct sign-editor sends and inbound sign saves are covered. |
| `ClientboundPlayerLookAtPacket` | explicit fallback X/Y/Z | covered | Covered while preserving entity-target metadata. |
| `ClientboundPlayerPositionPacket` | player X/Y/Z | intentionally not broadly virtualized | Existing lifecycle hooks canonicalize login, respawn, and wake-up before vanilla teleport ack state is created; normal in-session teleports should stay in the player's current coordinate space. |
| `ClientboundSetDefaultSpawnPositionPacket` | respawn `BlockPos` | intentionally canonical for now | Client stores dimension respawn data as a world anchor; do not virtualize unless a visible UI/navigation leak is proven. |
| `ClientboundTrackedWaypointPacket` | waypoint `Vec3i`, `ChunkPos`, or azimuth | covered | Block/chunk positions use wrapped aliases; azimuth angles use the shortest wrapped path. |
| Debug and GameTest packets | debug `BlockPos`/`ChunkPos` | low priority | Classify as intentionally ignored unless gameplay uses them. |

## Completed Implementation Record

### 1. World Event And Cosmetic Packets

Status: implemented.

Goal: cover packet families that are visible immediately during ordinary
survival play and often have both a packet coordinate and a raw-distance send
gate.

This landed in two small slices.

- Phase 1a: `PlayerList.broadcast(...)` packets only. This covers positional
  sounds, non-global level events, and block events.
- Phase 1b: custom `ServerLevel` per-player send paths. This covers block
  destruction, particles, explosions, and global level events.

Implemented by `mod-fabric/src/main/java/globe/world/util/WorldEventPacketUtil.java`.

Public API:

```java
public final class WorldEventPacketUtil {
    public static Packet<?> virtualizeFor(Packet<?> packet, ServerPlayer viewer);
    public static Vec3 virtualizePos(ServerLevel level, Vec3 pos, ServerPlayer viewer);
    public static BlockPos virtualizeBlockPos(ServerLevel level, BlockPos pos, ServerPlayer viewer);
    public static double wrappedDistanceSqr(ServerLevel level, Vec3 source, ServerPlayer viewer);
}
```

Helper rules:

- Return the original packet instance when X/Z are unchanged.
- Return the original packet instance for unknown packet classes.
- Copy packets per viewer whenever X/Z change; do not mutate shared packets.
- Name private copy helpers after the packet, for example
  `virtualizeSound(...)` and `virtualizeLevelEvent(...)`.
- Keep `wrappedDistanceSqr(...)` side-effect free so it can be used safely in
  multiple mixin hooks.

Packet copy behavior:

- `ClientboundSoundPacket`: copy sound, source, virtual X/Y/Z, volume, pitch,
  and seed.
- `ClientboundLevelEventPacket`: copy type, virtual `BlockPos`, data, and
  `globalEvent`.
- `ClientboundBlockEventPacket`: copy virtual `BlockPos`, block, `b0`, and
  `b1`.
- `ClientboundBlockDestructionPacket`: copy id, virtual `BlockPos`, and
  progress.
- `ClientboundLevelParticlesPacket`: copy particle, flags, virtual X/Y/Z,
  offsets, speed, and count.
- `ClientboundExplodePacket`: copy virtual center, radius, block count, player
  knockback, particles, sound, and block particle list. Do not virtualize the
  knockback vector; it is a relative push for that player.

Implemented by `PlayerListBroadcastMixin`.

- Target `PlayerList.broadcast(@Nullable Player, double, double, double,
  double, ResourceKey<Level>, Packet<?>)`.
- Wrap the X/Z distance calculation so tiled dimensions use wrapped distance to
  decide whether alias players receive the packet.
- Wrap `ServerGamePacketListenerImpl.send(Packet<?>)` and pass the selected
  packet through `WorldEventPacketUtil.virtualizeFor(packet, player)`.
- This covers positional sounds, non-global level events, and block events.

Mitigations:

- Keep the packet helper conservative because `PlayerList.broadcast(...)` is a
  broad vanilla path. Unknown packets must pass through unchanged.
- If local capture is brittle, prefer a `@WrapOperation` around
  `ServerGamePacketListenerImpl.send(Packet<?>)` where the loop-local
  `ServerPlayer player` is available. Avoid a hook that loses per-viewer
  context.
- Add debug-only logging for the first implementation pass that reports packet
  class, original coordinate, virtual coordinate, and whether wrapped distance
  changed the send decision.

Implemented by `ServerLevelWorldEventMixin`.

- In `ServerLevel.destroyBlockProgress(...)`, wrap the raw distance calculation
  and packet send. Use wrapped distance for send eligibility and virtualize
  `ClientboundBlockDestructionPacket` per receiving player.
- In `ServerLevel.sendParticles(ServerPlayer, boolean, double, double, double,
  Packet<?>)`, wrap the `closerToCenterThan(...)` decision with wrapped distance
  and virtualize the particle packet before send.
- In `ServerLevel.explode(...)`, wrap `player.distanceToSqr(center)` with
  wrapped distance and virtualize `ClientboundExplodePacket` before send.
- In `ServerLevel.globalLevelEvent(...)`, replace the center-of-block X/Z used
  to compute each player's 32-block sound position with the nearest alias of
  the event for that player. Keep vanilla's "global sound appears 32 blocks
  away if distant" behavior after the wrapped direction is chosen.

Mitigations:

- Treat `globalLevelEvent(...)` as packet-specific, not as a normal
  `BlockPos` relabel. Preserve vanilla's 32-block distant sound placement after
  choosing the wrapped direction from the player to the event.
- If global event behavior is ambiguous, leave global events for a follow-up
  subphase and ship the non-global phase 1a/1b fixes first.
- For each custom `ServerLevel` send path, fix send eligibility and packet
  coordinates in the same change. Do not land a coordinate copy that still uses
  raw canonical distance checks.
- If a mixin target fails because Mojang reshaped the method body, re-open the
  Minecraft 26.1.2 source and retarget the smallest send or distance operation
  that still has `ServerPlayer` context.

Phase 1 regression checklist:

- Breaking a block near each tile edge shows crack progress at every visible
  alias being interacted with and never at the canonical-only copy.
- Door/chest/note-block/piston block events render at the visible alias.
- Block break effects, extinguish/fire events, and other level events appear at
  the alias block.
- Sounds near an edge are audible from the nearest alias and pan from the
  visible location.
- Particles from commands, block effects, and gameplay spawn at the alias.
- TNT or creeper explosions near each edge show particles/sound at the alias
  and still knock the player in the expected direction.

### 2. Chunk Biome Resend Packets

Status: implemented.

Goal: when vanilla resends biome data for canonical chunks, clients that have
loaded alias chunks should receive biome payloads at those alias chunk keys.

Implemented in `ChunkPacketUtil` with:

```java
public static List<Packet<?>> virtualizeBiomeResendForLoadedAliases(
        ClientboundChunksBiomesPacket packet,
        ServerPlayer viewer);
```

Implementation details:

- Iterate each `ClientboundChunksBiomesPacket.ChunkBiomeData`.
- Canonicalize `data.pos()` through `CoordUtil.wrapChunk(...)`.
- Query `ChunkAliasTracker.aliasesForCanonical(...)` for that player and
  dimension.
- For each loaded alias, create a new `ChunkBiomeData(alias, data.buffer().clone())`.
- If no aliases are recorded, fall back to the nearest virtual chunk for that
  player, matching the light-update fallback style.
- Dedupe alias chunk positions before constructing the returned packet.
- Keep unrelated chunks grouped into as few packets as practical, but prefer
  correctness and readable code over clever batching.

Mitigations:

- Clone each biome payload buffer before reusing it under an alias chunk
  position.
- Dedupe by packed alias chunk key before constructing `ChunkBiomeData`.
- Keep batching simple at first: one packet per player containing the deduped
  alias biome data is acceptable if it is easier to prove correct.
- Add a debug-only summary with canonical chunk count, alias chunk count, and
  fallback count. This makes stale or missing alias tracker records visible
  without dumping biome payloads.
- If no reliable manual trigger for biome resends is available, add a temporary
  debug command or diagnostic hook for validation, then remove or guard it
  before keeping the implementation.

Implemented by `ChunkMapBiomeResendMixin`.

- Target `ChunkMap.resendBiomesForChunks(...)`.
- Wrap the `player.connection.send(ClientboundChunksBiomesPacket.forChunks(...))`
  call.
- Send every packet returned by the biome resend helper.

Phase 2 regression checklist:

- Trigger biome resend near all four edges if a vanilla or debug path is
  available.
- Verify grass, foliage, water, and fog-tinted biome visuals refresh for loaded
  aliases, not only for the canonical chunk.
- Confirm no biome packet is sent for aliases that are not in
  `ChunkAliasTracker`.

### 3. Entity-Tracking Residual Packets

Status: implemented.

Goal: close entity-related packet leaks that already pass through
`ChunkMapTrackedEntityMixin` but are not yet copied by `EntityPacketUtil`.

Implemented in `EntityPacketUtil.virtualizeFor(...)` with:

- `ClientboundDamageEventPacket`: if `sourcePosition` is present, copy it with
  virtual X/Z for the receiving viewer. Entity-id based damage sources can stay
  unchanged because the client resolves them through already virtualized entity
  positions.
- `ClientboundMoveVehiclePacket`: copy the vehicle position through nearest
  virtual X/Z for the rider/viewer.
- `ClientboundMoveMinecartPacket`: copy every
  `NewMinecartBehavior.MinecartStep` with virtualized `position`; keep movement,
  rotation, and weight unchanged.

`ClientboundSetEntityMotionPacket` is classified intentionally unchanged. Its
`Vec3` is entity velocity, not a world position.

Mitigations:

- Implement one packet family at a time and keep each copy helper private to
  `EntityPacketUtil` until reuse is proven.
- Preserve relative values exactly: velocity, movement, minecart step movement,
  rotations, weights, and player knockback are not world positions.
- For `ClientboundMoveMinecartPacket`, copy the list before changing any step
  position. Do not modify the packet's existing step list.
- Add source comments only where the distinction between absolute position and
  relative movement is easy to confuse.

Phase 3 regression checklist:

- Damage from an explosion, projectile, and point damage source near a seam
  produces client effects from the visible alias.
- Riding a boat, horse, or vehicle across a seam does not snap to the canonical
  copy.
- Minecarts crossing tile edges keep their interpolation path near the visible
  alias.

### 4. Player, Interaction, Waypoint, And Spawn Packets

Status: sign-editor, look-at, and waypoint slices implemented.
Player-position is intentionally not broadly virtualized because the packet
participates in vanilla teleport acknowledgement state. Spawn packets are
classified intentionally canonical unless testing proves a visible client-side
leak.

Goal: handle packets that are either player-self coordinates or UI/navigation
state and therefore need packet-specific semantics instead of blind wrapping.

Implemented or classified in separate packet-specific slices:

- `ClientboundOpenSignEditorPacket`: virtualize the sign `BlockPos` before it
  is sent from `ServerPlayer.openTextEdit(...)`. Reuse the same block-position
  helper as world events. Canonicalize the matching
  `ServerboundSignUpdatePacket` position before vanilla looks up the sign block
  entity, with the same alias-mutation guard used by block break/use. Keep
  `Player.isWithinBlockInteractionRange(...)` wrapped too, because the sign
  block entity clears edit permission if the player appears too far away while
  the editor is open.
- `ClientboundPlayerLookAtPacket`: add an accessor or constructor-copy helper
  because vanilla exposes only `getPosition(Level)`, not raw fields. Virtualize
  explicit fallback X/Z for the receiving player. If `atEntity` is true, keep
  the entity id and target anchor, but also virtualize the fallback coordinates.
- `ClientboundPlayerPositionPacket`: do not broadly virtualize this packet.
  Existing code canonicalizes login, respawn, and bed wake-up positions before
  vanilla creates teleport acknowledgement state. Normal in-session teleports
  should remain in the player's current coordinate space unless a specific leak
  is found and fixed together with the server-side awaited position.
- `ClientboundSetDefaultSpawnPositionPacket`: audit whether client UI,
  compass-like behavior, or respawn preview uses the packet position directly.
  If it does, virtualize to the nearest alias for each receiving player; if
  vanilla treats it as a dimension-level canonical anchor, document it as
  intentionally canonical.
- `ClientboundTrackedWaypointPacket`: block and chunk waypoint positions are
  copied to the nearest alias for the receiving player. Azimuth packets still
  carry an angle, but connection selection and angle calculation use wrapped
  distance and direction.

Mitigations:

- Do not add a generic `ClientboundPlayerPositionPacket` wrapper. If a later
  test finds a specific teleport leak, fix that teleport source before
  `ServerGamePacketListenerImpl.teleport(...)` stores
  `awaitingPositionFromClient`.
- Keep `ClientboundSetDefaultSpawnPositionPacket` canonical unless client
  behavior proves it is a visible player-relative navigation packet. Spawn is a
  world anchor, so blindly virtualizing it could fight the existing compass and
  respawn logic.
- For `ClientboundPlayerLookAtPacket`, prefer an accessor/copy helper that
  preserves the `atEntity` flag, entity id, and anchors exactly. Only the
  explicit fallback X/Z should move.
- For sign editing, virtualize both the preceding `ClientboundBlockUpdatePacket`
  and `ClientboundOpenSignEditorPacket` if they are sent directly from
  `ServerPlayer.openTextEdit(...)` outside the `ChunkHolder` fanout path, then
  canonicalize the inbound save packet so alias text edits reach the canonical
  sign. Preserve wrapped block interaction range so vanilla's sign edit
  permission does not expire against the canonical sign position.
- Waypoints need their own source-backed mini-audit before code changes because
  position, chunk, and azimuth waypoints have different semantics.

Phase 4 regression checklist:

- Opening and saving a sign editor from an alias edits the visible sign and
  writes the canonical sign text without closing due to a coordinate mismatch.
- `/tp` and `/lookat` style command behavior near seams points at the nearest
  visible target.
- Spawn/waypoint UI, if visible in the tested version, does not point across an
  entire tile when a nearer alias exists.
- Login, respawn, bed wake-up, and dimension transfer still canonicalize player
  storage where the existing lifecycle code expects that.

### 5. Documentation And Audit Artifacts

Completed in `docs/vanilla-mechanics/position-bearing-packets.md`.

Completed content:

- The source-search command or method used against the Minecraft 26.1.2 Loom
  source jar.
- A table of every `Clientbound*Packet` class with `BlockPos`, `ChunkPos`,
  `SectionPos`, `Vec3`, `PositionMoveRotation`, explicit X/Z fields, or known
  encoded position payloads.
- For each packet: field type, vanilla send site, client handler, and Globe
  classification.
- A short note for packets intentionally ignored because they are debug-only,
  relative-only, entity-id-only, or already transformed before packet
  construction.

Durable mod docs updated by this audit:

- `docs/mod-mechanics/client.md`: packet coverage paragraph and key files.
- `docs/mod-mechanics/blocks-and-ticks.md`: document block event, block
  destruction, sound, particle, and level-event behavior.
- `docs/mod-mechanics/entities.md`: document damage, vehicle, and minecart
  packet virtualization.
- `docs/mod-mechanics/maps.md`: mention why map packets are classified covered
  upstream rather than wrapped at send time.
- `docs/plans/README.md`: this plan is now in the completed list.

## Regression Validation

Manual validation should use a small tile size so a player can see multiple
aliases of the same canonical area at once.

Core scenarios:

1. Stand near each X edge, Z edge, and corner.
2. Repeat from the canonical tile and from a non-canonical alias.
3. Test with two players standing in different aliases of the same canonical
   area.
4. Trigger block events, level events, sounds, particles, explosions, block
   break progress, sign editing, biome resends, entity damage, vehicle movement,
   minecart movement, look-at commands, respawn, and dimension transfer.
5. Check both send eligibility and packet coordinates. A wrapped distance fix
   is incomplete if the packet is still canonical; a packet copy is incomplete
   if vanilla's distance check prevents the player from receiving it.

Useful debug logging for future regressions:

- packet class
- original coordinate
- canonical coordinate
- viewer position
- virtual coordinate
- whether send eligibility used wrapped distance
- alias fanout count for packets that use `ChunkAliasTracker`

Keep permanent logs behind `GlobeWorld.LOGGER.isDebugEnabled()`.

If manual reproduction is too slow, add temporary debug commands that emit one
packet family at a chosen canonical/alias position. Keep those commands
debug-only or remove them after the regression is diagnosed.

## Risks And Rules

- Do not mutate a shared packet instance that may be sent to multiple players.
  Copy packets per viewer.
- Do not fan out to mathematically possible aliases unless the client has loaded
  them. Use `ChunkAliasTracker` for chunk-state packets.
- For broadcast packets with range checks, fix both the range check and the
  packet coordinate.
- Keep relative movement, velocity, knockback, and entity-id-only packets
  unchanged unless a source audit proves they encode absolute position.
- Avoid a broad catch-all rewrite of every packet. Every packet family should
  have a source-backed reason and a regression scenario.
- Keep mixin names tied to the vanilla path they intercept, for example
  `PlayerListBroadcastMixin`, `ServerLevelWorldEventMixin`, and
  `ChunkMapBiomeResendMixin`.
- After a Minecraft version bump, rerun the packet inventory against the new
  Loom source jars before trusting this classification.
