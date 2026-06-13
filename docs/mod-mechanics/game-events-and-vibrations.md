# Game Events And Vibrations

## What

Server-side game-event dispatch is topology-aware when dimension tiling is
enabled. Sculk sensors, calibrated sensors, shriekers, wardens, allays, and
other game-event listeners can receive events across X/Z tile seams as if the
canonical tile were continuous.

Untiled dimensions continue through vanilla `GameEventDispatcher`.

## Why

Vanilla dispatch scans listener sections around the raw event position. A
listener that is visually nearby through a tile seam can therefore be raw-far
from the event section and never receive the event. Vibration listeners then
also measure source distance, travel time, particle origin, and occlusion from
the raw event source.

Globe World keeps canonical listener identity and canonical block/entity state,
but gives each listener the event source in its nearest visible alias frame.

## Dispatch

`GameEventDispatcherMixin` replaces `GameEventDispatcher.post(...)` only when
the level's topology is enabled.

`TopologicalGameEvents.post(...)` mirrors the vanilla dispatcher with three
topological changes:

- The visible event notification box is split into canonical boxes and then
  canonical listener sections.
- Each canonical section is visited once, capped to the canonical X/Z tile and
  the level build-height sections.
- Listener instances are deduped by identity before immediate delivery or
  distance-ordered queueing.

For each section, the helper maps the canonical event source into a
section-local alias before calling
`GameEventListenerRegistry.visitInRangeListeners(...)`. This keeps the
registry's vanilla raw-radius prefilter useful even for listeners across a
seam.

For each listener found by the registry, the helper maps the canonical event
source again into the listener-local frame. Immediate listeners receive that
source directly. `BY_DISTANCE` listeners are queued with the listener-local
source so vanilla ordering uses the visible distance from the listener.

Debug game-event broadcasts are emitted once at the canonical event source.

## Vibration Behavior

`VibrationSystem.Listener` receives the listener-local source from the
topological dispatcher. Vanilla's validation path then uses that source for:

- `canReceiveVibration(...)` source block position;
- vibration occlusion line checks;
- scheduled `VibrationInfo` origin;
- source-to-listener distance and travel time;
- vibration particle origin.

The stored `GameEvent.Context` object is not copied or rewritten. Source
entities, affected block states, projectile owners, and other vanilla identity
data remain the canonical server objects.

Because existing block/chunk access wraps alias coordinates into canonical
storage, the vanilla occlusion line checks follow the short visible path when
the dispatcher passes an alias-frame vibration origin. Blocks on the raw long
path do not become part of that listener-local clip.

## Key Files

- `mod-fabric/src/main/java/globe/world/topology/TopologicalGameEvents.java`
- `mod-fabric/src/main/java/globe/world/mixin/GameEventDispatcherMixin.java`
- `mod-fabric/src/main/resources/globe-world.mixins.json`

## Related Vanilla Mechanics

- `net.minecraft.world.level.gameevent.GameEventDispatcher`
- `net.minecraft.world.level.gameevent.GameEventListenerRegistry`
- `net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry`
- `net.minecraft.world.level.gameevent.vibrations.VibrationSystem`
- `net.minecraft.world.level.gameevent.vibrations.VibrationInfo`
