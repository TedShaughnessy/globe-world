# Entity Visual Alias Rendering

Status: implemented as an MVP for non-player, not-leashed entities, including
non-player mounted stacks.
Durable behavior now lives in
[client mechanics](../mod-mechanics/client.md) and
[entity mechanics](../mod-mechanics/entities.md). This file remains as the
historical design note and validation checklist for future broadening.

## Goal

Render nearby visual copies of a canonical entity at every loaded tile alias
around the camera, so mobs look continuous across wrapped worlds without
creating duplicate server entities.

The server should still own exactly one canonical mob. The client should still
track exactly one vanilla entity id. Alias copies are presentation-only draws
and alias-aware picks that map back to that same canonical entity.

## Current Behavior

Entity storage and packets are canonical/server-authoritative. For each viewer,
entity packets are virtualized to the nearest visible alias. When the nearest
alias changes, `ChunkMapTrackedEntityMixin` sends an absolute position sync for
the same client entity id.

This works, but tiny tiles expose two visual problems:

- Only one alias of the mob is visible, even if several aliases of the same
  canonical chunk are on screen.
- When the nearest alias changes, vanilla may interpolate the position sync, so
  the mob appears to slide from one alias to another.

## Desired Model

- One real server entity.
- One real client entity.
- Multiple render submissions for that entity, offset by whole tile widths in
  X/Z.
- Entity interaction and attack picking tests those alias-offset bounding boxes
  but returns the canonical entity id.
- Server-side interaction, reach, damage, AI, pathfinding, collision, ticking,
  saving, and networking remain canonical.

## Vanilla Anchors

Minecraft 26.1.2 client sources:

- `LevelRenderer.submitEntities(...)` iterates `ClientLevel.entitiesForRendering()`,
  extracts an `EntityRenderState`, and calls `EntityRenderDispatcher.submit(...)`.
- `EntityRenderDispatcher.submit(...)` translates by the submitted relative
  X/Y/Z and then delegates to the entity renderer.
- `EntityRenderer.extractRenderState(...)` stores interpolated absolute
  `state.x`, `state.y`, and `state.z`, plus distance-to-camera data used by
  names, shadows, and score labels.
- `ClientPacketListener.handleEntityPositionSync(...)` and
  `handleTeleportEntity(...)` interpolate small position jumps for ticking
  entities.

Project hooks:

- `mod-fabric/src/main/java/globe/world/mixin/ChunkMapTrackedEntityMixin.java`
- `mod-fabric/src/main/java/globe/world/util/EntityPacketUtil.java`
- `mod-fabric/src/main/java/globe/world/util/GlobeCurvedRaycast.java`
- `mod-fabric/src/client/java/globe/world/client/mixin/LocalPlayerMixin.java`

## Implementation Plan

### 0. Feature Toggle And Activation Rules

Add a client-side feature toggle before implementing rendering. Entity aliases
are presentation-only, so the toggle should live with client/debug settings
rather than world generation settings.

Suggested modes:

- `OFF`: never render entity aliases and do not run alias entity picking.
- `AUTO`: default. Render aliases only when tiling is enabled and a neighboring
  tile can fall inside the effective entity render radius.
- `FORCE_DEBUG`: run alias enumeration even when the automatic large-tile skip
  would normally avoid it, but still do final distance/frustum/chunk culling
  before submitting any render copy.

The automatic gate should happen before per-entity alias enumeration:

```text
tileWidth = tiling.tileSizeBlocks()
maxPossibleEntityRenderRadius = current effective entity render distance

if mode == OFF:
    skip aliases

if mode == AUTO and maxPossibleEntityRenderRadius < tileWidth:
    skip aliases for the frame
```

This keeps large tiles cheap. If mobs cannot normally render one tile away,
visual aliases are irrelevant and the renderer should behave like vanilla.

Debug behavior:

- Expose the current mode in the Globe debug HUD.
- Add a keyboard/debug toggle near the existing `F3+Y`/tile-border debug
  controls, or add a temporary config option if a keybind would be noisy.
- When aliases are disabled by the automatic radius gate, show that state in
  diagnostics so it is clear the feature is not broken.
- Alias-aware picking should follow the same toggle. If aliases are not being
  rendered, the pick path should not select invisible alias copies.

### 1. Shared Alias Offset Utility

Add a small utility, probably `GlobeEntityAliasRenderer` or
`GlobeVisualAliasUtil`, under `mod-fabric/src/client/java/globe/world/client/`.

Responsibilities:

- Respect the entity alias feature mode.
- Return no offsets when dimension tiling is disabled.
- Read tile width from `DimensionTiling.forLevel(level).tileSizeBlocks()`.
- Compute the effective vanilla entity render distance for the entity, including
  the current entity-distance scaling and the entity's own render-distance
  multiplier.
- Early-skip alias work when the tile is larger than any possible visible alias
  distance for that entity. Large tiles should usually do no clone enumeration.
- Enumerate only neighboring tile offsets whose alias AABB can intersect the
  entity render-distance sphere around the camera.
- Bound final alias submissions by vanilla's effective entity render distance,
  plus the entity bounding radius. Alias copies outside the distance where the
  mob would normally render must not be submitted.
- Prefer offsets whose alias block position is inside the loaded/rendered chunk
  view, so aliases are only drawn where the terrain copy exists.
- Dedupe offsets that would produce the same visible position.

The first version should be radius-driven rather than hard-coded to a fixed
3x3 workload:

```text
renderRadius = effective vanilla entity render distance for this entity
padding = entity bounding radius
maxOffset = ceil((renderRadius + padding) / tileWidth)

if maxOffset == 0:
    skip alias enumeration

for offsetX in [-maxOffset, maxOffset]
for offsetZ in [-maxOffset, maxOffset]
    skip (0, 0)
    aliasBox = entityBox moved by offsetX * tileWidth, offsetZ * tileWidth
    keep only if aliasBox is within renderRadius of the camera
```

For most large tiles, `AUTO` mode will skip the whole feature before this point,
or `maxOffset` will be `0`, so the feature adds no clone checks. For tiny tiles,
the radius naturally expands to the few neighboring tile copies that could be
visible, while still respecting vanilla mob render range.

### 2. Render Alias Copies

Add a client mixin for `LevelRenderer.submitEntities(...)`.

Preferred hook:

- Inject or wrap just after the vanilla `EntityRenderDispatcher.submit(...)`
  call for the real entity.
- Reuse the current entity loop locals: `entity`, extracted render `state`,
  camera X/Y/Z, `PoseStack`, and `SubmitNodeCollector`.
- For each visual alias offset, submit the same canonical entity again at the
  alias-relative render position.

Important detail: do not only change the `x/z` arguments passed to
`EntityRenderDispatcher.submit(...)`. Some renderers use `state.x/state.z` for
shadow placement, distance checks, name labels, and renderer-specific effects.
For correctness, create an alias render state or temporarily offset mutable
state fields before submission, then restore them.

MVP acceptable scope:

- Render `LivingEntity` mobs and other non-player entities.
- Skip the camera entity and local player.
- Render non-player mounted stacks by sharing the root vehicle's alias offsets
  across vehicle and passenger render states.
- Keep vanilla rendering for the canonical submission.

Follow-up scope:

- Leashes.
- Remote player aliases if wanted.
- Projectiles and item entities.
- Nameplate distance based on the alias distance, not the canonical distance.
- Shadow/light sampling from the alias position.

### 3. Alias-Aware Frustum And Chunk Visibility

The normal `EntityRenderDispatcher.shouldRender(...)` culls against the
canonical entity position. Alias copies need their own culling.

For each alias candidate:

- Build an alias AABB by moving the entity bounding box by the tile offset.
- Reject it when its nearest point to the camera is outside the normal effective
  entity render distance for that entity.
- Check the camera frustum against that alias AABB.
- Check that the alias block position is in a compiled/visible section before
  submitting it, matching the vanilla guard in `LevelRenderer.submitEntities`.
- Keep the existing special-case behavior for passenger chains only after
  passenger aliases are implemented.

This prevents drawing hidden aliases behind unloaded terrain and keeps tiny-tile
worlds from submitting a large number of invisible copies.

### 4. Alias-Aware Entity Picking

Extend `GlobeCurvedRaycast.pickEntity(...)`.

Current behavior queries real client entities inside the ray search area and
clips against their canonical bounding boxes. After visual aliases exist, the
pick path must also test alias-shifted bounding boxes.

Implementation sketch:

1. Build the same curved ray segment list.
2. Build the same search AABB.
3. For each pickable entity, test the canonical box plus visual alias boxes
   that overlap the search AABB.
4. If an alias box is hit, return `new EntityHitResult(entity, aliasHitPoint)`.
5. Keep the selected entity as the canonical entity, so vanilla interaction
   packets still target the real entity id.

Server-side wrapped range checks already exist for entity interactions, so the
server should accept a nearby visual alias of a canonical entity instead of
measuring only raw Euclidean distance.

### 5. Snap Wrap-Rebases Instead Of Interpolating

Visual aliases should reduce how often rebases matter, but the underlying
vanilla client entity can still be moved when the server changes the nearest
alias. Add a separate client-side correction to prevent visible sliding.

Add a client mixin around `ClientPacketListener.handleEntityPositionSync(...)`
and, if needed, `handleTeleportEntity(...)`.

Detection rule:

- Tiling enabled.
- Existing entity and incoming position differ mostly by one or more whole tile
  widths in X and/or Z.
- Wrapped delta between old and new positions is small, while raw delta is
  tile-sized.

Behavior:

- Snap the entity to the new alias position and reset old position/rotation
  state, instead of letting vanilla `moveOrInterpolateTo(...)` animate it.
- Do not apply this to the local player.
- Apply to non-player mounted stacks only; player-controlled vehicles still
  need dedicated multiplayer validation before enabling the snap path.
- When a non-player vehicle stack snaps, refresh passenger old-position state
  after vanilla rider positioning so multiple passengers do not render from the
  previous tile alias.

This phase is independent from alias rendering. It fixes the remaining visual
case where the real client entity changes its alias.

### 6. Diagnostics

Keep the feature toggle visible while developing and retain useful diagnostics
after the MVP.

Useful diagnostics:

- Current alias-rendering mode: `OFF`, `AUTO`, or `FORCE_DEBUG`.
- Whether aliases were skipped because entity render distance is smaller than
  tile width.
- Count alias entity submissions per frame.
- Count culled alias candidates.
- Show selected alias offset for the current crosshair entity in the debug HUD.
- Log snap-on-rebase events at debug level with entity id, type, old position,
  new position, and tile offset.

Remove noisy logs before considering the feature complete, but keep counters if
they are useful in the Globe debug overlay.

## Risks And Edge Cases

- **Mutable render state:** entity render states are renderer-specific and may
  hold derived absolute positions. Offsetting fields must be careful and
  restored after each alias submission.
- **Name labels and score labels:** distance-to-camera should be recomputed per
  alias, otherwise labels may appear or disappear based on the canonical copy.
- **Leashes:** leash render state likely stores both endpoints. Alias rendering
  needs both the mob and leash holder to be shifted coherently.
- **Passengers:** mounted stacks should render together at the same alias
  offset, not as separate nearest aliases.
- **Sounds:** entity-bound sounds will still follow the one canonical client
  entity. Visual alias sounds are out of scope for MVP.
- **Particles:** entity-generated particles will still originate at the
  canonical client entity unless specific packet/event paths are copied.
- **Performance:** tiny tiles and high entity counts can multiply submissions.
  Culling and an alias submission cap may be needed.
- **Remote players:** player aliases have social/UI implications, especially
  nameplates and tab/outline behavior. Defer unless explicitly desired.

## Validation Checklist

- In a tiny tile world, one mob near a tile seam is visible in every loaded
  terrain alias around the player.
- Moving the player across a tile boundary does not make the mob slide across
  the screen.
- Attacking or interacting with a rendered alias targets the canonical mob.
- Entity outlines, shadows, and name labels appear at the visible alias
  position or are intentionally disabled for MVP.
- No duplicate mobs appear in server debug commands or save data.
- Mob AI, collision, despawn, and pathfinding continue to operate once per
  canonical entity.
- Entity rendering remains stable with high render distance on a small tile.
- Vehicles/passengers are either explicitly supported or explicitly skipped
  without crashes.

## Completion Criteria

- Durable behavior is moved into `docs/mod-mechanics/client.md` and
  `docs/mod-mechanics/entities.md`.
- This plan is moved from active plans to completed plans, with any remaining
  edge cases preserved as audits.
- Manual validation confirms visual alias rendering, alias picking, and
  snap-on-rebase behavior in at least one small Overworld tile.
