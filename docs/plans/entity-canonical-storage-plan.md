# Entity Canonical Storage Plan

## Problem

The canonical tile should own mutable entity state, but the current entity path
only canonicalizes mobs and item entities when they are added to `ServerLevel`.
Item entities are canonicalized again after ticking; mobs, vehicles, projectiles,
XP orbs, and other moving entities are not.

This means an entity can cross a tile edge and remain stored in an alias chunk.
Once that happens, wrapped tracking and viewer-relative packets are working from
a non-canonical backing position.

Accepted limitation: a single real entity may still render in only the nearest
visible alias for a player. This plan does not try to render multiple copies of
one entity id at once.

## Current Hooks

- `EntityCanonicalizer` wraps X/Z and syncs packet position codecs.
- `ServerLevelEntityMixin` canonicalizes mobs and item entities before storage.
- `ItemEntityMixin` canonicalizes item entities after ticking.
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

Files:

- `src/main/java/globe/world/util/EntityCanonicalizer.java`
- `src/main/java/globe/world/mixin/ServerLevelEntityMixin.java`

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

Files:

- `src/main/java/globe/world/util/EntityCanonicalizer.java`

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
5. Apply the same X/Z shift to every indirect passenger and sync each passenger
   packet position codec after the snap.
6. Allow player passengers to be shifted only as part of a vehicle stack. This is
   the narrow exception to "players are not canonicalized during normal play";
   without it, vanilla's mounted positions can diverge from the canonical
   vehicle. Keep direct standalone player movement under `PlayerCanonicalizer`.

Return `true` when a wrap occurred. This return value is useful for diagnostics
and for future packet-resync work.

Implementation detail: use `getIndirectPassengers()` or the recursive passenger
stream rather than only direct passengers, so chest boats, mounted mobs, and
stacked test cases move as one unit.

### 3. Canonicalize after server entity ticks

Files:

- Add `src/main/java/globe/world/mixin/ServerLevelEntityTickMixin.java`
- Remove `src/main/java/globe/world/mixin/ItemEntityMixin.java` after the generic
  hook covers item entities.
- Update `src/main/resources/globe-world.mixins.json`.

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

Files:

- Add `src/main/java/globe/world/mixin/EntityTeleportCanonicalizationMixin.java`
  or fold this into the generic entity tick mixin if the target class stays
  small.
- Update `src/main/resources/globe-world.mixins.json`.

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

### 5. Ensure first post-wrap packet is absolute when needed

Files:

- `src/main/java/globe/world/util/EntityCanonicalizer.java`
- `src/main/java/globe/world/mixin/ChunkMapTrackedEntityMixin.java`
- `src/main/java/globe/world/util/EntityPacketUtil.java`

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

### 6. Add diagnostics before manual testing

Files:

- `src/main/java/globe/world/GlobeDebugCommands.java`
- `src/main/java/globe/world/util/EntityCanonicalizer.java`

Add a debug command or extend an existing one to report:

- Entity id, type, UUID, block position, chunk position, and canonical chunk.
- Whether `shouldCanonicalizeContinuously(...)` applies.
- Whether the entity is a passenger or root vehicle.
- For a selected area or all loaded entities, count non-player entities currently
  outside canonical X/Z.

This gives a fast way to validate manual tests without reading save files after
every case.

### 7. Manual validation sequence

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

Ask the user to run:

- `./gradlew build`
- `./gradlew runClient`

### 8. Documentation updates when implemented

Files:

- `docs/mod-mechanics/entities.md`
- `docs/vanilla-mechanics/mobs-and-entities.md`
- `docs/plans/README.md`
- This file.

Move the durable behavior into `docs/mod-mechanics/entities.md`:

- Non-player entities canonicalize on add/load, after ticks, and after
  same-dimension teleports.
- Mounted stacks are shifted together; standalone player movement remains
  virtual during a session.
- Packet codecs are synced after canonical wraps.

Update `docs/vanilla-mechanics/mobs-and-entities.md` with the exact vanilla
anchors used during implementation. When the validation list is complete, move
this plan to completed in `docs/plans/README.md` or keep only residual packet or
vehicle notes if something remains unresolved.
