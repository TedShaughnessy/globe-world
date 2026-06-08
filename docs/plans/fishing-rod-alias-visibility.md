# Fishing Rod Alias Visibility

## Status

Planned. Fishing bobbers are canonical non-player entities, while players can
stand in raw alias coordinates during normal play. Vanilla fishing logic and
the client fishing-line renderer still assume both sides are in one Euclidean
coordinate frame.

## Symptom

Using a fishing rod outside the canonical tile can fail immediately, or the
cast can appear partially wrong: the bobber, fishing line, splash/bubble/fish
approach particles, or pulled loot/entity can be missing or offset depending on
whether the visible water is in the canonical tile or an alias.

The intended behavior is that fishing looks and works the same from either
frame. A player standing at an alias water copy should see the bobber, line,
fish approach particles, splash particles, caught loot, and hooked entities in
that same visible alias.

## Vanilla Mechanics

Checked against Minecraft 26.1.2 Loom sources.

`FishingRodItem.use(...)`:

- If `player.fishing != null`, calls `player.fishing.retrieve(itemStack)`.
- Otherwise constructs `new FishingHook(player, level, luck, lureSpeed)`.
- Spawns the hook with `Projectile.spawnProjectile(...)`.

`FishingHook(Player, Level, int, int)`:

- Stores the player as owner.
- Snaps the hook to `player.getX()`, `player.getEyeY()`, and `player.getZ()`
  with a small hand-facing offset.
- Computes initial horizontal velocity from the player's rotation.

`FishingHook.tick(...)`:

- Calls `shouldStopFishing(owner)` every server tick.
- `shouldStopFishing(...)` discards the hook unless the owner still holds a
  fishing rod and `this.distanceToSqr(owner) <= 1024.0`.
- Bobbing fish approach particles are emitted from the hook's current X/Z with
  `ServerLevel.sendParticles(...)`.

`FishingHook.retrieve(...)` and `pullEntity(...)`:

- Use raw `owner.getX() - this.getX()` and `owner.getZ() - this.getZ()` deltas
  to pull caught loot or hooked entities toward the owner.

`FishingHookRenderer.extractRenderState(...)`:

- Computes the line from `getPlayerHandPos(owner, ...)` to the hook position.
- This client renderer does not know that the hook packet may have been
  virtualized into a viewer-relative alias while the owner entity position is in
  another tile frame.

## Current Globe Behavior

Covered paths:

- `EntityCanonicalizer` canonicalizes non-player entities before storage and
  after ticks. Fishing bobbers are therefore stored in canonical X/Z.
- `EntityPacketUtil` virtualizes add, teleport, and absolute sync packets per
  viewer. The bobber can be rendered at the viewer-nearest alias even though it
  is stored canonically.
- `ServerLevelWorldEventMixin` and `WorldEventPacketUtil` already virtualize
  `ClientboundLevelParticlesPacket` positions and use wrapped distance checks
  for direct particle sends.

Gaps:

- `FishingHook.shouldStopFishing(...)` uses raw Euclidean distance from the
  canonicalized hook to the raw alias owner. Outside the canonical tile, this
  can exceed 32 blocks immediately and discard the hook.
- `FishingHook.retrieve(...)` and `FishingHook.pullEntity(...)` use raw X/Z
  pullback deltas. Once the discard gate is fixed, caught items or hooked
  entities can be pulled across a whole tile instead of toward the visible
  owner alias.
- `FishingHookRenderer` computes the fishing line from raw owner hand position
  to virtualized hook position. This can make the line vanish into the wrong
  tile or stretch across the world.
- Fishing approach particles are probably covered by existing particle packet
  virtualization, but they need a fishing-specific validation case because
  their positions are generated from the canonical hook.

## Goal

Make fishing fully alias-frame aware while preserving single canonical server
ownership of the bobber and any caught mutable state.

## Non-Goals

- Do not create per-alias fishing hook entities on the server.
- Do not change fishing loot tables, open-water rules, lure timing, enchantment
  behavior, or rod durability rules.
- Do not implement general toroidal projectile collision in this pass. The
  bobber should work for ordinary casts into visible wrapped water; broader
  projectile seam collision remains separate.

## Implementation Plan

### 1. Add A Fishing Hook Server Mixin

Create:

- `mod-fabric/src/main/java/globe/world/mixin/FishingHookMixin.java`

Target:

- `net.minecraft.world.entity.projectile.FishingHook`

Register it in:

- `mod-fabric/src/main/resources/globe-world.mixins.json`

Wrap `shouldStopFishing(Player)` around:

```java
Entity.distanceToSqr(Entity)
```

Replacement behavior:

```java
return CoordUtil.wrappedDistanceSqr((Entity) (Object) this, owner);
```

Only replace the distance value. Keep vanilla's rod-in-hand and
`canInteractWithLevel()` checks unchanged.

Expected effect:

- A bobber canonicalized into the tile remains close to its owner in wrapped
  space, so vanilla no longer discards it just because the owner is standing in
  an alias.

### 2. Make Fishing Pullback Deltas Toroidal

In the same `FishingHookMixin`, wrap the X/Z owner-minus-hook subtractions in:

- `FishingHook.retrieve(ItemStack)`, for caught loot velocity.
- `FishingHook.pullEntity(Entity)`, for hooked entity velocity.

Suggested targets:

```java
@ModifyExpressionValue(method = "retrieve", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getX()D"))
@ModifyExpressionValue(method = "retrieve", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getZ()D"))
```

or use `@WrapOperation` around the subtraction inputs if the compiled bytecode
is easier to target that way.

Concrete behavior:

- For X, replace `owner.getX() - hook.getX()` with
  `CoordUtil.wrappedDeltaBlock(level, owner.getX(), hook.getX())`.
- For Z, replace `owner.getZ() - hook.getZ()` with
  `CoordUtil.wrappedDeltaBlock(level, owner.getZ(), hook.getZ())`.
- Leave Y as vanilla.

Prefer a small `@Unique` helper in the mixin:

```java
@Unique
private double globeWorld$ownerPullDeltaX(Entity owner) {
    FishingHook hook = (FishingHook) (Object) this;
    return CoordUtil.wrappedDeltaBlock(hook.level(), owner.getX(), hook.getX());
}
```

Expected effect:

- Loot and hooked entities move toward the owner's visible alias instead of
  accelerating across a whole tile.

### 3. Add A Client Fishing Hook Renderer Mixin

Create:

- `mod-fabric/src/client/java/globe/world/client/mixin/FishingHookRendererMixin.java`

Target:

- `net.minecraft.client.renderer.entity.FishingHookRenderer`

Register it in:

- `mod-fabric/src/client/resources/globe-world.client.mixins.json`

Patch:

- `FishingHookRenderer.extractRenderState(FishingHook, FishingHookRenderState, float)`

Goal:

- Ensure `FishingHookRenderState.lineOriginOffset` is computed between the hook
  and the owner hand in the same visual tile frame.

Suggested approach:

1. Let vanilla compute the render state.
2. At method tail, get the hook owner.
3. Recompute the owner hand position or adjust the already-computed
   `lineOriginOffset`.
4. Move the owner hand X/Z to the nearest alias relative to the hook render
   position:

```java
double handX = hookPos.x + state.lineOriginOffset.x;
double handZ = hookPos.z + state.lineOriginOffset.z;
double aliasHandX = CoordUtil.virtualBlock(entity.level(), CoordUtil.wrapBlock(entity.level(), handX), hookPos.x);
double aliasHandZ = CoordUtil.virtualBlock(entity.level(), CoordUtil.wrapBlock(entity.level(), handZ), hookPos.z);
state.lineOriginOffset = new Vec3(aliasHandX - hookPos.x, state.lineOriginOffset.y, aliasHandZ - hookPos.z);
```

Notes:

- `state.x/state.y/state.z` should be checked against
  `FishingHookRenderState`/`EntityRenderState` source before implementing; use
  the actual hook render-state position available in Minecraft 26.1.2.
- This adjustment should be client-only and presentation-only. It must not move
  the player, the hook entity, or server state.
- Validate both first-person local-player fishing and third-person/remote-player
  fishing. Remote players can have their own visual alias behavior, so the line
  endpoint must follow the bobber's rendered alias, not blindly the raw owner
  entity position.

Expected effect:

- The fishing line connects the visible hand/rod to the visible bobber instead
  of crossing a tile boundary.

### 4. Validate Fish Approach Particles Before Adding New Packet Code

Fishing approach and bite effects are emitted by `FishingHook.catchingFish(...)`
through `ServerLevel.sendParticles(...)`.

Existing Globe hooks should already cover this path:

- `ServerLevelWorldEventMixin.sendParticlesWithWrappedDistance(...)` uses
  wrapped distance checks.
- `WorldEventPacketUtil.virtualizeParticles(...)` sends particle X/Z in the
  receiver-nearest alias.

Validation-first rule:

- Do not add a fishing-specific particle packet path unless a playtest proves
  the existing direct particle hook misses one of the fishing particle overloads.

If validation fails:

1. Inspect the exact `ServerLevel.sendParticles(...)` overload called by
   `FishingHook.catchingFish(...)`.
2. Add or broaden a `ServerLevelWorldEventMixin` injection so every
   `ClientboundLevelParticlesPacket` emitted by fishing goes through
   `WorldEventPacketUtil.virtualizeFor(...)`.
3. Keep particle packet positions in the same viewer-relative tile frame as the
   bobber entity packets.

### 5. Document The Implemented Mechanics

After implementation, update:

- `docs/mod-mechanics/entities.md`
  - Add fishing bobbers to the entity gameplay notes.
  - Explain wrapped owner distance and pullback deltas.
  - Replace or narrow the open-audit sentence about projectile physics if this
    fishing-specific work is complete but general projectiles remain deferred.
- `docs/mod-mechanics/client.md`
  - Add the fishing-line renderer adjustment and particle validation note.
- `docs/vanilla-mechanics/mobs-and-entities.md` or a new fishing subsection if
  it becomes useful after implementation.

Keep this plan until fishing has been playtested across canonical and alias
tiles. Then fold durable behavior into mod mechanics and retire or trim this
plan.

## Files To Change

- `mod-fabric/src/main/java/globe/world/mixin/FishingHookMixin.java`
- `mod-fabric/src/main/resources/globe-world.mixins.json`
- `mod-fabric/src/client/java/globe/world/client/mixin/FishingHookRendererMixin.java`
- `mod-fabric/src/client/resources/globe-world.client.mixins.json`
- `docs/mod-mechanics/entities.md`
- `docs/mod-mechanics/client.md`
- Optional, only if validation finds a particle gap:
  `mod-fabric/src/main/java/globe/world/mixin/ServerLevelWorldEventMixin.java`
  and/or `mod-fabric/src/main/java/globe/world/util/WorldEventPacketUtil.java`

## Validation

Manual playtest cases:

- Cast from inside the canonical tile into canonical water.
- Cast from an X alias tile into visible alias water.
- Cast from a Z alias tile into visible alias water.
- Cast from a corner alias tile into visible alias water.
- Stand with the player on one side of a tile edge and the bobber on the other
  visible side; the bobber should not instantly disappear.
- Wait through lure, approach particles, bite splash, retrieve, and loot
  pullback in each case.
- Hook an item entity or mob near a seam and retrieve it; pull direction should
  be toward the visible player alias.
- Third-person view: fishing line should connect hand to bobber.
- First-person view: fishing line should connect rod/hand to bobber without a
  long tile-sized segment.
- Multiplayer or second-client view: one player watches another fish from a
  different alias frame; bobber and line should still be coherent.

Suggested diagnostics while testing:

- Use `/globeworld entity <bobber>` if entity diagnostics can target the hook.
- Compare raw distance and wrapped distance between player and bobber.
- Watch for any `FishingHook.recreateFromPacket(...)` owner errors in the
  client log.

## Open Questions

- Does `FishingHookRenderer.extractRenderState(...)` expose enough render-state
  coordinates to adjust the line at tail, or is a more precise wrap around
  `Vec3.subtract(...)` cleaner?
- Are fish approach particles always emitted through the already-hooked
  `ServerLevel.sendParticles(ServerPlayer, ..., Packet)` path?
- Should a fishing bobber that physically crosses a tile edge during flight get
  a fishing-specific collision wrap, or is that deferred with general
  projectile seam collision?
