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

`GlobeLocalDaylight` builds gameplay predicates and local timeline samplers on
top of those helpers. In scrolling mode, tiled sky-light dimensions without
fixed time can ask whether a specific block/entity position is locally bright,
locally dark, or in the vanilla monster-burning part of the Overworld day
cycle. Timeline-driven gameplay attributes such as villager schedules, bee
hive behavior, and turtle egg hatch chance can also be sampled at local X.

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
- `src/main/java/globe/world/util/GlobeLocalDaylight.java`
- `src/main/java/globe/world/mixin/PlayerLocalSleepMixin.java`
- `src/main/java/globe/world/mixin/ServerPlayerLocalSleepMixin.java`
- `src/main/java/globe/world/mixin/ServerLevelLocalSleepTimeMixin.java`
- `src/main/java/globe/world/mixin/EnvironmentAttributeSystemBuilderGameplayMixin.java`
- `src/main/java/globe/world/mixin/MonsterLocalDaylightMixin.java`
- `src/main/java/globe/world/mixin/PhantomSpawnerLocalDaylightMixin.java`
- `src/main/java/globe/world/mixin/MobLocalDaylightMixin.java`
- `src/main/java/globe/world/mixin/PatrolSpawnerLocalDaylightMixin.java`
- `src/client/java/globe/world/client/GlobeScrollingSky.java`

The helper uses `Level.getDefaultClockTime()` for level-based overloads and also
exposes overloads that accept a `DimensionTiling` and explicit world time. The
explicit overloads are used by the client scrolling sky layers and are also
intended for gameplay hooks that already have a sampled clock value.

Sleeping checks wrap vanilla `BedRule.canSleep(...)` so `WHEN_DARK` is evaluated
at the bed/player position. When enough players sleep, the server still advances
the shared default clock, but the target time is the local morning for a sleeping
player instead of absolute vanilla morning. In multiplayer, Globe chooses the
sleeping player furthest from their next local morning, so valid local-night
sleepers are not left in night immediately after the skip.

Monster natural spawning wraps the no-argument `getMaxLocalRawBrightness(...)`
path in `Monster.isDarkEnoughToSpawn(...)`, so the brightness test uses local
sky darkening while thunder keeps vanilla's explicit thunder darkening. Phantom
spawning opens vanilla's player loop in scrolling mode and then applies the
phantom darkening threshold at each player position. Undead burning wraps
`Mob.isSunBurnTick(...)` so both the `MONSTERS_BURN` predicate and the
brightness curve use the mob's local solar phase.

Timeline-driven gameplay attributes are installed as positional layers while
scrolling mode is active. Villager and baby villager schedules use the local
solar phase at the villager position. Bee hive entry uses local
`BEES_STAY_IN_HIVE`, turtle eggs use local `TURTLE_EGG_HATCH_CHANCE`, and the
same local timeline path covers cat waking gifts, eyeblossoms, creaking, and
other direct callers of the localized attributes. Clock item daytime display
already samples `SUN_ANGLE` at the item owner position, so it follows the local
client sky layer instead of needing a separate item hook.

Patrol spawning has an extra global `ServerLevel.isBrightOutside()` gate before
it chooses a player or spawn position. In scrolling mode, that gate is opened so
the patrol path can run, and the actual spawn-position check then requires local
daylight at the patrol spawn position. Raids themselves do not have a vanilla
day/night start gate in 26.1.2; when villager raid behavior resets back to the
normal schedule, it uses the localized villager schedule attribute.

Stored sky light propagation remains global; local gameplay predicates supply
position-aware darkening at their call sites.

## Related Mechanics

- [Scrolling Day/Night](scrolling-day-night.md)
