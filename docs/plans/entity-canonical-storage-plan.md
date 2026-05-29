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

## Proposed Implementation

1. Define which entity classes should be canonicalized continuously.
   - Include mobs, item entities, vehicles, projectiles, XP orbs, falling blocks,
     and other non-player entities that live in finite world space.
   - Exclude players during normal play.
   - Consider excluding entities that are changing dimensions until after the
     transfer finishes.

2. Add a generic server-side post-move or post-tick canonicalization hook.
   - Prefer the narrowest vanilla hook that runs after entity position/chunk
     bookkeeping is safe to update.
   - Audit vanilla `Entity`, `ServerLevel`, and entity section storage sources
     before choosing the injection point.

3. Handle passengers and vehicles as one unit.
   - Test horse, boat, minecart, and mounted mob crossings.
   - If canonicalizing a vehicle, ensure passenger positions stay coherent.
   - Avoid independently wrapping a passenger away from its vehicle.

4. Keep packet position codecs in sync.
   - Reuse or extend `EntityCanonicalizer`.
   - Confirm relative movement packets after a wrap do not encode a huge delta.
   - Force an absolute position sync when a wrap happened and the viewer already
     tracks the entity.

5. Broaden storage-on-add canonicalization.
   - Replace the current `Mob` and `ItemEntity` allowlist with the shared
     predicate from step 1.
   - Keep player lifecycle canonicalization separate.

6. Document the resulting mechanics.
   - Move the durable explanation into `docs/mod-mechanics/entities.md`.
   - Update open audits after horse/vehicle and projectile testing.

## Validation

- Spawn a mob near each tile edge and push/path it across; verify it remains in
  a canonical chunk.
- Ride a horse, boat, and minecart across each edge; verify no unexpected
  teleport loop or dismount.
- Throw arrows, tridents, snowballs, and ender pearls across edges.
- Drop XP and items across edges.
- Use two players near opposite sides of the tile and verify tracked entity
  positions remain viewer-relative.
- Check entity save data after crossing; stored X/Z should be canonical for
  non-player entities.

