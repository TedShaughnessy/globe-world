# Entity Canonical Storage Plan

## Original Problem

The canonical tile should own mutable entity state, but the original entity path
only canonicalized mobs and item entities when they were added to `ServerLevel`.
Item entities were canonicalized again after ticking; mobs, vehicles,
projectiles, XP orbs, and other moving entities were not.

That meant an entity could cross a tile edge and remain stored in an alias
chunk. Once that happened, wrapped tracking and viewer-relative packets were
working from a non-canonical backing position.

Accepted limitation: a single real entity may still render in only the nearest
visible alias for a player. This plan does not try to render multiple copies of
one entity id at once.

## Current Hooks

- `EntityCanonicalizer` wraps X/Z and syncs packet position codecs.
- `ServerLevelEntityMixin` canonicalizes finite-world non-player entities before
  storage.
- `ServerLevelEntityTickMixin` canonicalizes finite-world non-player entities
  after server root/passenger ticks.
- `EntityTeleportCanonicalizationMixin` canonicalizes finite-world non-player
  entities after same-level teleport positioning.
- `EntityPassengerPositionMixin` keeps player passengers in their visible
  virtual tile when canonical non-player vehicles position riders.
- `ServerGamePacketListenerImplMixin` maps inbound player-controlled vehicle
  movement from the client's visible alias frame into the nearest storage frame,
  then canonicalizes the mounted stack after accepted vehicle moves.
- `ChunkMapTrackedEntityMixin` virtualizes entity tracking and outbound packets.
- `PlayerCanonicalizer` canonicalizes players only at lifecycle boundaries.

## Goals

- Keep all finite-world non-player entities stored at canonical X/Z after normal
  movement and teleports.
- Avoid double ticking or removing/re-adding entities unnecessarily.
- Preserve the intentional player behavior: players can travel in virtual
  coordinates during a session.
- Include rideable entities and passengers without causing client snap loops.

## Vanilla Anchors

Use these source anchors before editing the mixins:

- `ServerLevel.tick(...)` checks `entity.chunkPosition()` against entity-ticking
  range before calling `tickNonPassenger`.
- `ServerLevel.tickNonPassenger(...)` ticks the root entity, then recursively
  ticks passengers through `tickPassenger(...)`.
- `ServerLevel.tickPassenger(...)` calls `rideTick(...)` for mounted entities.
- `Entity.setPosRaw(...)` updates `blockPosition` and `chunkPosition`, then calls
  `levelCallback.onMove()`.
- `PersistentEntitySectionManager.Callback.onMove()` moves the entity between
  sections and starts/stops tracking or ticking when the effective section
  visibility changes.
- `Entity.teleportSameDimension(...)` teleports passengers before the root
  entity. `teleportCrossDimension(...)` recreates the entity and re-adds it to
  the target level.

The key implementation consequence is that `EntityCanonicalizer` can keep using
`snapTo(...)`: it flows through `setPosRaw(...)`, updates section storage, and
does not require remove/re-add.

## Concrete Implementation Plan

### 1. Make canonicalization policy explicit

Status: implemented.

Files:

- `mod-fabric/src/main/java/globe/world/util/EntityCanonicalizer.java`
- `mod-fabric/src/main/java/globe/world/mixin/ServerLevelEntityMixin.java`

Add one shared predicate:

- `shouldCanonicalizeContinuously(Entity entity)`

Rules:

- Return `false` on the logical client.
- Return `false` when tiling is disabled for the entity's dimension.
- Return `false` for removed entities.
- Return `false` for `ServerPlayer` during normal play.
- Return `false` for entities whose current level is not a `ServerLevel`.
- Return `true` for other finite-world entities, including mobs, item entities,
  vehicles, projectiles, XP orbs, falling blocks, and marker-like non-player
  entities.

Then replace the `Mob` / `ItemEntity` allowlist in
`ServerLevelEntityMixin.canonicalizeStoredEntity(...)` with this predicate. This
makes storage-on-add, legacy chunk load, and worldgen chunk entity load all use
the same policy.

### 2. Add a root-stack canonicalizer

Status: implemented.

Files:

- `mod-fabric/src/main/java/globe/world/util/EntityCanonicalizer.java`

Add helpers with names close to:

- `canonicalizeAfterTick(Entity entity)`
- `canonicalizeRootStack(Entity root)`
- `canonicalizeSingle(Entity entity)`
- `shiftPassengerStack(Entity passenger, double dx, double dz)`

Behavior:

1. `canonicalizeAfterTick(entity)` ignores passengers. Passenger position is
   owned by the vehicle tick path, so independently wrapping a passenger can
   split a mounted stack.
2. If the entity is a root and `shouldCanonicalizeContinuously(root)` is true,
   compute canonical X/Z and the shift delta.
3. If no X/Z shift is needed, return `false`.
4. Snap the root to canonical X/Z and sync its packet position codec.
5. Apply the same X/Z shift to every indirect non-player passenger and sync each
   passenger packet position codec after the snap.
6. Do not canonicalize `ServerPlayer` passengers as entity storage. Instead,
   `EntityPassengerPositionMixin` keeps them in the visible virtual tile nearest
   their current server position when a canonical non-player vehicle positions
   riders. This keeps chunk streaming aligned with the client while the
   non-player vehicle remains canonical.

Return `true` when a wrap occurred. This return value is useful for diagnostics
and for future packet-resync work.

Implementation detail: use `getIndirectPassengers()` or the recursive passenger
stream rather than only direct passengers, so chest boats, mounted mobs, and
stacked test cases move as one unit.

### 3. Canonicalize after server entity ticks

Status: implemented.

Files:

- Added `mod-fabric/src/main/java/globe/world/mixin/ServerLevelEntityTickMixin.java`
- Removed `mod-fabric/src/main/java/globe/world/mixin/ItemEntityMixin.java` after the generic
  hook covers item entities.
- Updated `mod-fabric/src/main/resources/globe-world.mixins.json`.

Mixin hooks:

- Inject at `TAIL` of `ServerLevel.tickNonPassenger(Entity entity)`.
- Inject at `TAIL` of `ServerLevel.tickPassenger(Entity vehicle, Entity entity)`.

Both injections call `EntityCanonicalizer.canonicalizeAfterTick(...)`.

Expected behavior:

- Root non-player entities wrap once after their whole root/passenger tick has
  run.
- Passenger entities are skipped by `canonicalizeAfterTick(...)`, so the
  `tickPassenger` hook is mainly a safety net for weird passenger/root state
  changes.
- Item entities no longer need their special `ItemEntityMixin`.
- Entities that teleport or move during their tick end the tick stored in a
  canonical section.

### 4. Canonicalize same-dimension teleports

Status: implemented.

Files:

- Added `mod-fabric/src/main/java/globe/world/mixin/EntityTeleportCanonicalizationMixin.java`.
- Updated `mod-fabric/src/main/resources/globe-world.mixins.json`.

Mixin hooks:

- Inject at `TAIL` of the two-argument
  `Entity.teleportSetPosition(PositionMoveRotation currentValues,
  PositionMoveRotation destination, Set relatives)` overload. Do not also inject
  the one-argument overload, because it delegates to the two-argument overload.
- Inject at `TAIL` of `Entity.teleportTo(double x, double y, double z)` to cover
  direct same-level teleports that use `snapTo(...)` and `teleportPassengers()`
  without a `TeleportTransition`.

Rules:

- Use `shouldCanonicalizeContinuously(...)`.
- Do not canonicalize `ServerPlayer`.
- If the entity is already `CHANGED_DIMENSION` or otherwise removed, skip it.
  Newly created target-dimension entities may be canonicalized before
  `ServerLevel.addDuringTeleport(...)`; storage-on-add canonicalization is the
  second safety net.
- For passenger teleports, prefer the root-stack helper so offsets remain
  coherent.

This closes the gap where command/plugin/portal-like same-dimension teleports
place non-player entities in an alias and they do not tick again before saving or
tracking.

### 5. Canonicalize client-controlled vehicle movement

Status: implemented.

Files:

- Updated `mod-fabric/src/main/java/globe/world/mixin/ServerGamePacketListenerImplMixin.java`.
- Updated `mod-fabric/src/main/java/globe/world/mixin/EntityPassengerPositionMixin.java`.
- Updated `mod-fabric/src/main/java/globe/world/util/EntityCanonicalizer.java`.

Vanilla `ServerGamePacketListenerImpl.handleMoveVehicle(...)` treats
`ServerboundMoveVehiclePacket.position()` as an absolute server position. When a
player is riding through an alias, the client reports the mount at that visible
alias coordinate, so vanilla can snap the horse, pig, boat, or other controlled
vehicle into an alias entity section before the next entity tick.

Mixin hooks:

- Modify the method argument at `HEAD` of `handleMoveVehicle(...)`.
- Inject at `TAIL` of `handleMoveVehicle(...)`.

Behavior:

1. Wrap the packet X/Z to canonical coordinates.
2. Re-expand that canonical coordinate to the virtual copy nearest vanilla's
   `vehicleLastGoodX/Z`, so ordinary movement and crossing a tile edge remain a
   small delta for vanilla's movement checks.
3. Let vanilla validate collisions, movement speed, and vehicle correction as
   usual.
4. Position direct passengers on the accepted vehicle. Player passengers remain
   in their visible virtual tile; non-player passengers use the vehicle storage
   frame.
5. Canonicalize the mounted stack, then refresh `vehicleFirstGood*` and
   `vehicleLastGood*` to the canonicalized vehicle position. This prevents a
   second packet in the same connection tick from seeing a full-tile jump.

### 6. Ensure first post-wrap packet is absolute when needed

Status: not implemented. Keep this as a fallback until manual testing shows a
client snap or large relative-delta issue after wrapping.

Files:

- `mod-fabric/src/main/java/globe/world/util/EntityCanonicalizer.java`
- `mod-fabric/src/main/java/globe/world/mixin/ChunkMapTrackedEntityMixin.java`
- `mod-fabric/src/main/java/globe/world/util/EntityPacketUtil.java`

Current code already syncs packet position codecs after canonicalization and
virtualizes add, teleport, and absolute position-sync packets. Keep relative
movement packets relative.

Add only if testing shows a client snap or large relative delta after a wrap:

- Track entity ids that wrapped this tick in `EntityCanonicalizer`.
- In `ChunkMapTrackedEntityMixin`'s tracked-entity send path, when a tracked
  entity is marked as wrapped, send or force a `ClientboundEntityPositionSyncPacket`
  through `EntityPacketUtil.virtualizeFor(...)` before normal relative movement.
- Clear the marker after the tracking tick.

Do not add this extra resync until a reproducible packet issue appears. It is a
fallback, not the first implementation step.

### 7. Add diagnostics before manual testing

Status: implemented.

Files:

- `mod-fabric/src/main/java/globe/world/GlobeDebugCommands.java`
- `mod-fabric/src/main/java/globe/world/util/EntityCanonicalizer.java`

Added `/globeworld debug entity <target>` and `/globeworld debug entities` to
report:

- Entity id, type, UUID, block position, chunk position, and canonical chunk.
- Whether `shouldCanonicalizeContinuously(...)` applies.
- Whether the entity is a passenger or root vehicle.
- For a selected area or all loaded entities, count non-player entities currently
  outside canonical X/Z.

This gives a fast way to validate manual tests without reading save files after
every case.

### 8. Manual validation sequence

Status: pending.

Run these in a small tile, ideally with two players or one client plus logs:

1. Spawn a zombie, cow, armor stand, item, XP orb, falling sand, arrow, trident,
   snowball, fireball, and ender pearl near each edge. Push or launch them across
   X+, X-, Z+, and Z-. The debug command should report canonical chunks after
   each tick.
2. Ride a horse, boat, chest boat, minecart, and saddled pig across each edge.
   Verify the vehicle and all passengers remain one mounted stack, with no
   dismount loop or repeated correction packet.
3. Put a non-player passenger on a vehicle, such as a mob in a boat, and move the
   stack across an edge. Verify both entities stay canonical and tracked.
4. Teleport a non-player entity to alias coordinates with a command, then check
   that it stores canonical immediately.
5. Use two players near opposite sides of the tile and verify the same canonical
   entity appears in the viewer-relative nearest alias for both.
6. Save and reload after crossing. Non-player entity save data should contain
   canonical X/Z.

During each pass, use `/globeworld debug entity <target>` for the entity under
test and `/globeworld debug entities` for the current dimension. The summary
should report `outside canonical=0` after each crossing settles.

Ask the user to run:

- `./gradlew build`
- `./gradlew runClient`

### 9. Documentation updates when implemented

Status: implemented. Core mechanics, diagnostics, and vanilla notes are updated;
manual validation cases remain above.

Files:

- `docs/mod-mechanics/entities.md`
- `docs/vanilla-mechanics/mobs-and-entities.md`
- `docs/plans/README.md`
- This file.

Move the durable behavior into `docs/mod-mechanics/entities.md`:

- Non-player entities canonicalize on add/load, after ticks, and after
  same-dimension teleports.
- Mounted non-player stacks are shifted together; player passengers remain in
  the visible virtual tile used for chunk streaming; standalone player movement
  remains virtual during a session.
- Packet codecs are synced after canonical wraps.

Update `docs/vanilla-mechanics/mobs-and-entities.md` with the exact vanilla
anchors used during implementation.
