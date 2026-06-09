# Coordinate Frames

## Problem

V1 uses ordinary Minecraft position types for several different meanings:
canonical storage, raw player coordinates, viewer-facing packet coordinates,
nearest target aliases, and worldgen scoped coordinates. The math is mostly
centralized in `CoordUtil`, but the semantic frame is carried in the caller's
head.

That makes review hard. A `BlockPos` can be correct in one method and subtly
wrong after it crosses into another subsystem.

## V2 Direction

Introduce explicit frame concepts, even if the implementation stays lightweight:

- `CanonicalBlockPos` and `CanonicalChunkPos`: durable state owner.
- `RawBlockPos` and `RawChunkPos`: vanilla coordinate supplied by Minecraft.
- `VirtualBlockPos` and `VirtualChunkPos`: alias coordinate visible to a
  player/client.
- `ActorLocalPos`: nearest alias of a target relative to an actor.
- `WirePos`: position encoded for one packet receiver.
- `GenerationPos`: position inside a toroidal worldgen view.

These do not all need to be heavy wrapper classes on day one. The important
rule is that conversions are named at boundaries:

```text
raw -> canonical
canonical -> virtual for viewer
canonical target -> actor-local alias
wire position -> canonical authoritative target
```

## Requirements

- Every conversion needs a dimension-aware `TopologyContext`.
- Helpers must preserve vanilla Y behavior unless a specific feature says
  otherwise.
- Conversion names should say which frame they return.
- Debug output should report both frame and value, not just coordinates.

## Expected Benefits

- Easier code review: the desired frame is visible in signatures.
- Fewer repeated decisions about when to wrap.
- Better diagnostics when a bug is a frame mismatch rather than bad math.
- Cleaner migration toward shared entity query and raycast primitives.

## Open Questions

- Should frame wrappers be Java records around vanilla types, or should v2 use
  a smaller helper API first?
- Which vanilla-facing methods must still accept raw `BlockPos`/`Vec3` to avoid
  excessive allocation or invasive patches?
- Can frame annotations or naming conventions catch most mistakes without
  wrapping every temporary value?
