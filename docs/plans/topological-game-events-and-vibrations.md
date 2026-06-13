# Topological Game Events And Vibrations Plan

This plan resolves the game-event and vibration gap raised in the
[Minecraft coordinate coverage audit](minecraft-coordinate-coverage-audit.md).

## Problem

Vanilla game events are dispatched from a raw event position. The dispatcher
scans listener sections around that raw position, and vibration listeners then
measure raw distance, raw occlusion, raw travel time, and raw particle origins.

In Globe World, a sculk sensor, shrieker, warden, allay, or other listener can
be visually close through a tile seam but raw-far in vanilla coordinates. Packet
virtualization does not fix this because the server-side listener decision has
already happened.

## Goals

- Deliver each game event to each real listener at most once.
- Let listeners see the event from their nearest visible alias frame.
- Preserve canonical listener identity and canonical block/entity state.
- Use topological occlusion where vanilla would check line of sight.
- Avoid multiplying event work without a radius cap.

## Non-Goals

- Client-only particle or debug rendering polish.
- Replacing unrelated sound/particle packet virtualization.
- Persisting alias listener sections.

## Vanilla Source Anchors

- `net/minecraft/world/level/gameevent/GameEventDispatcher.java`
- `net/minecraft/world/level/gameevent/GameEventListener.java`
- `net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java`
- `net/minecraft/world/level/gameevent/vibrations/VibrationSystem$Listener`
- `net/minecraft/world/level/gameevent/vibrations/VibrationSystem$Data`
- Sculk sensor, calibrated sensor, shrieker, allay, and warden callers.

## Current Globe Anchors

- `TopologyContext`
- `TopologicalRaycasts`
- `WorldEventPacketUtil`
- `ServerLevelWorldEventMixin`
- `entities.md`
- `packet-policies.md`

## Proposed Design

Add a `TopologicalGameEvents` helper under `globe.world.topology`.

The helper should expose one main dispatch operation:

- `post(level, event, visibleSource, context)`

The operation should:

- keep the original vanilla event context object intact;
- compute canonical listener-section ranges touched by the visible event radius;
- visit each listener section once per canonical section;
- dedupe listener instances by identity;
- compute a listener-local source position by moving the canonical event source
  to the nearest alias of the listener position;
- invoke vanilla listener handling with the listener-local source when possible.

If vanilla APIs do not allow a listener-local source without copying dispatcher
logic, keep the copied logic narrow and source-anchored in comments.

## Concrete Implementation Plan

### 1. Add `TopologicalGameEvents`

Create `mod-fabric/src/main/java/globe/world/topology/TopologicalGameEvents.java`.

Public API:

- `static boolean post(ServerLevel level, Holder<GameEvent> event, Vec3 source, GameEvent.Context context)`
- `static Vec3 listenerLocalSource(TopologyContext topology, Vec3 canonicalSource, Vec3 listenerPos)`
- `static List<SectionPos> listenerSections(ServerLevel level, Vec3 source, int radius)`

Return value:

- `true` means the helper handled and replaced vanilla dispatch.
- `false` means topology is disabled and the vanilla method should continue.

Implementation details:

- Early-return `false` when `TopologyContexts.forLevel(level).enabled()` is
  false.
- Canonicalize the event source once with `TopologyContext.canonicalBlock(Vec3)`.
- Build a visible event AABB using the vanilla notification radius, split it
  into canonical boxes, then convert those boxes into canonical section ranges.
- Visit each canonical `(sectionX, sectionY, sectionZ)` once. Use a packed
  section set to dedupe ranges.
- For each section, obtain the canonical chunk through
  `level.getChunkSource().getChunkNow(sectionX, sectionZ)`.
- Before calling `GameEventListenerRegistry.visitInRangeListeners(...)`, compute
  a section-local source by virtualizing the canonical source toward the center
  of that section. This satisfies the registry's internal raw radius filter.
- In the registry visitor, compute a listener-local source from the canonical
  source and the listener position passed by the registry.
- Dedupe listeners by identity before queueing or invoking them.
- Preserve vanilla delivery mode:
  - immediate listeners call `listener.handleGameEvent(level, event, context,
    listenerLocalSource)`;
  - `BY_DISTANCE` listeners are collected in `GameEvent.ListenerInfo` with the
    listener-local source, then sorted with vanilla ordering.
- Broadcast debug game-event info once at the canonical source or at the
  original visible source. Prefer canonical source for server debug consistency;
  note the choice in docs when implemented.

Risk mitigations:

- Keep the helper as a narrow copy of
  `GameEventDispatcher.post(...)`; do not replace listener registration or
  registry storage.
- Use identity dedupe because the same listener can be reachable through
  multiple split sections when radii or tiny tiles overlap.
- Preserve the original `GameEvent.Context` object so source entities, affected
  states, and tags keep vanilla identity.
- Cap section iteration to the canonical tile when the event radius is wider
  than the tile.

### 2. Add `GameEventDispatcherMixin`

Create `mod-fabric/src/main/java/globe/world/mixin/GameEventDispatcherMixin.java`.

Hook:

- `@Mixin(GameEventDispatcher.class)`
- shadow the private final `ServerLevel level`.
- `@Inject(method = "post", at = @At("HEAD"), cancellable = true)`
- call `TopologicalGameEvents.post(level, gameEvent, position, context)`;
  cancel only when it returns `true`.

Register the mixin in `globe-world.mixins.json`.

### 3. Add Vibration-Specific Helpers Only Where Needed

The dispatcher should pass a listener-local source into
`VibrationSystem.Listener.handleGameEvent(...)`, which means vanilla's raw
distance and occlusion checks will often become correct without a broad
vibration rewrite. Add extra vibration mixins only for cases still wrong after
manual testing.

Candidate helper:

- `TopologicalVibrations.isOccluded(level, origin, destination)`: same
  semantics as vanilla `VibrationSystem.Listener.isOccluded(...)`, but uses
  `TopologicalRaycasts` or canonicalized clip endpoints when the origin is an
  alias.

Candidate mixin:

- `VibrationSystemListenerMixin`: wrap the private static `isOccluded(...)`
  call inside `handleGameEvent(...)` if listener-local dispatch alone does not
  handle all cases.

Keep `VibrationInfo.pos()` in the listener-local frame for travel particles and
travel time. This is acceptable because the info is transient listener state,
not durable world ownership. The source entity in `GameEvent.Context` remains
the real canonical entity.

### 4. Add Diagnostics If Needed

Add `GAME_EVENTS` or `VIBRATIONS` to `DiagnosticsChannel` only if manual tests
are hard to interpret.

Log fields:

- event key;
- raw source;
- canonical source;
- section-local source;
- listener position;
- listener-local source;
- delivery mode;
- dedupe count.

## Vibration Handling

Vibrations need more than event delivery. They measure source/listener distance
and sometimes test occlusion.

Add a small adapter for `VibrationSystem.Listener` that:

- maps the event source into the listener-local frame before distance checks;
- uses wrapped distance for travel time;
- routes occlusion through `TopologicalRaycasts.topologicalClip(...)` or a new
  `topologicalLineOfSight(level, from, to, collisionContext)` overload;
- keeps stored vibration target positions canonical unless vanilla requires a
  travel path/particle source in visible coordinates.

Acceptance criteria:

- A sculk sensor can hear a step/block event across an X seam, Z seam, and
  corner seam.
- A shrieker warning and warden vibration can be triggered across a seam.
- Occluding blocks in the visible path block the vibration; blocks on the raw
  long path do not incorrectly block it.
- The same listener is not notified twice when the radius covers both canonical
  and alias ranges.

## Implementation Phases

### Phase 1: Source Audit

Create a short vanilla mechanics note for game events if implementation needs
more than the existing audit. Record:

- dispatcher section scan;
- listener registration/storage key;
- vibration accept/reject path;
- occlusion and travel-time methods.

### Phase 2: Dispatch Helper

Implement `TopologicalGameEvents` and hook the dispatcher entry point only when
dimension tiling is enabled. Keep untiled dimensions vanilla.

### Phase 3: Vibration Listener Adapter

Patch vibration distance, occlusion, and travel-time calculations. Prefer
wrapping exactly the distance/clip calls over replacing the entire tick path.

### Phase 4: Diagnostics

Add optional diagnostics on a new or existing channel if debugging proves hard:

- raw event source;
- canonical source;
- listener position;
- listener-local source;
- accepted/rejected reason.

## Tests And Manual Checks

- Step, projectile, block place, and block break near a seam with a sculk sensor
  across the visible edge.
- Calibrated sensor frequency behavior across a seam.
- Sculk shrieker and warden response across a seam.
- Allay game-event listener across a seam.
- Occlusion wall placed on the visible short path and then on the raw long path.

## Documentation Updates When Implemented

- Document durable behavior in `docs/mod-mechanics/entities.md` or a new
  `docs/mod-mechanics/game-events.md` if the section becomes large.
- Add a vanilla mechanics page if source notes are substantial.
- Shrink the game-event section in
  `minecraft-coordinate-coverage-audit.md`.
