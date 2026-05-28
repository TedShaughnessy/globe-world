# Local Solar Time

## What

Scrolling day/night mode treats canonical block X as longitude. Callers can ask
for a local solar time at a block, entity, or camera position without changing
the level's actual clock.

The shared helper lives in `CoordUtil`:

- `MINECRAFT_DAY_TICKS`
- `longitudeOffsetTicks(...)`
- `localSolarTimeTicks(...)`
- `localSolarDayTicks(...)`

`localSolarTimeTicks(...)` returns the monotonic world clock plus the longitude
offset. `localSolarDayTicks(...)` normalizes that value into `[0, 24000)` for
day-track sampling and gameplay predicates.

## Why

Minecraft stores one clock per world or dimension clock. Globe World should not
rewrite that clock as the player moves because doing so would make global time
non-monotonic and would affect unrelated systems. Local solar time is derived
at the call site instead.

## Longitude Math

The canonical tile is centered on origin. For a tiled dimension:

```text
canonicalX = wrapBlock(x)
longitudeOffsetTicks = canonicalX / tileSizeBlocks * 24000
localSolarTimeTicks = worldClockTicks + longitudeOffsetTicks
localSolarDayTicks = localSolarTimeTicks mod 24000
```

The middle of the tile has no offset. The west and east sides are approximately
half a Minecraft day behind and ahead of the world clock, and the wrapped X seam
maps back to the same solar phase modulo one day.

For untiled dimensions the longitude offset is `0`, so local solar time matches
the vanilla clock.

## Implementation

- `src/main/java/globe/world/util/CoordUtil.java`
- `src/client/java/globe/world/client/GlobeScrollingSky.java`

The helper uses `Level.getDefaultClockTime()` for level-based overloads and also
exposes overloads that accept a `DimensionTiling` and explicit world time. The
explicit overloads are used by the client scrolling sky layers and are also
intended for gameplay hooks that already have a sampled clock value.

## Related Plans

- [Realistic Globe Lighting](../plans/realistic-lighting-plan.md)
