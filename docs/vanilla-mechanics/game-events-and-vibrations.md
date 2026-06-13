# Game Events And Vibrations

## Source Anchors

- `net/minecraft/world/level/gameevent/GameEventDispatcher.java`
- `net/minecraft/world/level/gameevent/GameEventListener.java`
- `net/minecraft/world/level/gameevent/GameEventListenerRegistry.java`
- `net/minecraft/world/level/gameevent/EuclideanGameEventListenerRegistry.java`
- `net/minecraft/world/level/gameevent/GameEvent.java`
- `net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java`
- `net/minecraft/world/level/gameevent/vibrations/VibrationInfo.java`
- `net/minecraft/world/level/gameevent/vibrations/VibrationSelector.java`

## Dispatcher Flow

`GameEventDispatcher.post(...)` gets the event notification radius from the
`GameEvent`, converts the raw event position to a block position, and scans all
section X/Y/Z coordinates touched by that raw radius.

For each scanned X/Z section, the dispatcher asks
`ServerChunkCache.getChunkNow(sectionX, sectionZ)` for the chunk, then visits
the section's `GameEventListenerRegistry`.

Immediate listeners are invoked during the scan. `BY_DISTANCE` listeners are
collected as `GameEvent.ListenerInfo`, sorted by squared raw distance from the
source to the listener position, and invoked after the scan.

## Listener Registry

`EuclideanGameEventListenerRegistry.visitInRangeListeners(...)` stores real
listener instances for a chunk section. It asks each listener's
`PositionSource` for its current position and keeps only listeners whose block
position is within `listener.getListenerRadius()` of the raw source position.

The registry does not know about world topology. The source position passed to
the registry controls its raw-radius filter.

## Vibration Listener

`VibrationSystem.Listener.handleGameEvent(...)` rejects events when a vibration
is already active, when the event is not in the user's listenable event tags, or
when the user-specific `canReceiveVibration(...)` method rejects the source
block.

If accepted so far, the listener checks occlusion by tracing from the source
block center to the listener block center against blocks tagged
`OCCLUDES_VIBRATION_SIGNALS`. It nudges the source in each direction and treats
the vibration as unoccluded if any nudged line is clear.

Accepted vibrations are stored as `VibrationInfo` with the event, source
distance, source position, and source entity identity. The ticker later uses
that distance for travel time, sends vibration particles from the stored source
position, and calls `User.onReceiveVibration(...)` when the travel time expires.

## Topology Audit Questions

- Is the dispatcher scanning listener sections in the same visible frame as the
  event?
- Is the registry radius filter receiving a source position near the listener
  section?
- Are `BY_DISTANCE` listeners sorted by visible distance rather than raw
  across-tile distance?
- Does the vibration source position describe the visible path used for
  occlusion, travel time, and particles?
