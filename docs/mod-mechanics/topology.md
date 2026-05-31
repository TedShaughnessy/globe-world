# Topology

## What

Globe World treats X/Z as periodic. A raw position can be anywhere in vanilla
coordinate space, but mutable state is stored at its canonical equivalent inside
the configured tile.

Tiling is also dimension-specific:

- The Overworld uses `mode` and `tile_size`.
- The Nether uses `nether_mode` and the effective Nether tile size.
- The End never tiles.

## Why

Every gameplay system needs the same answer to coordinate identity questions. If
chunk lookup, entity tracking, block packets, and worldgen each invent their own
wrapping math, edge behavior will drift and aliases will either desync or
duplicate state.

Vanilla dimensions also do not share one coordinate scale. Nether portals have
an 8:1 relation with the Overworld, while the End should remain vanilla. Keeping
tiling policy dimension-aware avoids applying Overworld topology to dimensions
where it does not fit.

## Coordinate Helpers

The coordinate helper layer answers four questions:

- What canonical chunk/block corresponds to this raw coordinate?
- Which tile alias does this raw coordinate belong to?
- What is the shortest wrapped X/Z distance between two positions?
- Where should a canonical object be rendered relative to a specific viewer?

`CoordUtil` is the single source for wrapping and virtual-coordinate math. It
supports raw helpers, level-aware helpers, dimension-aware helpers, and helpers
that use the current worldgen/scoped tiling context.

Canonicalization is used before state access. Virtualization is used when
building viewer-facing positions, especially packets and tracking decisions.

## Dimension Policy

`DimensionTiling` resolves the effective tiling context for Overworld, Nether,
and End. Runtime paths use level/player dimension context where available.
Worldgen paths that do not receive a `ServerLevel` use a scoped
`DimensionTiling` context. Scoped context helpers restore any previous tiling
after nested worldgen calls, cancellations, or exceptions, so Nether generation
does not accidentally inherit the Overworld fallback.

`nether_one_eighth_overworld_size` is effective only when the Overworld tile size
is at least `16` chunks and cleanly divisible by 8. This keeps the derived
Nether tile at or above the smallest supported Overworld preset.

Examples:

- Overworld `1024` chunks and one-eighth enabled -> Nether `128` chunks.
- Overworld `8` chunks and one-eighth requested -> the request is cleared and
  Nether uses `8` chunks.
- Overworld `1025` chunks and one-eighth requested -> the request is cleared
  and Nether uses `1025` chunks.
- Nether disabled -> no Nether wrapping.

This keeps vanilla's 8:1 portal scale coherent only when the configured
Overworld period supports it exactly.

## Key Files

- `src/main/java/globe/world/util/CoordUtil.java`
- `src/main/java/globe/world/util/DimensionTiling.java`
- `src/main/java/globe/world/config/TilingSettings.java`
- `src/main/java/globe/world/config/GlobeConfig.java`
- `src/main/java/globe/world/mixin/WorldGenSettingsMixin.java`
- `src/main/java/globe/world/mixin/ServerLevelTicksDimensionMixin.java`
- `src/main/java/globe/world/mixin/NetherPortalBlockMixin.java`
- `src/main/java/globe/world/mixin/PortalProcessorMixin.java`

## Implemented Paths

- Runtime chunk, block, and entity packet paths use dimension context.
- Server chunk lookup, alias tickets, random ticks, spawning collection,
  tracking, block mutation, and worldgen region access use dimension-aware
  wrapping.
- Worldgen and natural-spawn paths that still need ambient coordinate helpers
  enter them through scoped `DimensionTiling` wrappers.
- Players are rebased to canonical X/Z on login, bed wake-up, and respawn.
- Scheduled tick containers are tagged with their `ServerLevel` dimension when
  exposed by `ServerLevel`.
- Nether portal approximate exits canonicalize the source X/Z before applying
  vanilla's dimension scale, then wrap the target dimension before portal
  search/creation. This keeps different aliases of the same source portal from
  creating separate scaled target portals.
- `/globeworld debug pos` reports the current dimension's effective tiling.

## Related Vanilla Mechanics

- [Vanilla chunk loading](../../vanilla-mechanics/chunk-loading.md)
- [Vanilla block updates](../../vanilla-mechanics/block-updates.md)
- [Vanilla mobs and entities](../../vanilla-mechanics/mobs-and-entities.md)
- [Vanilla world generation](../../vanilla-mechanics/world-generation.md)

## Open Audits

- Nether terrain/noise periodicity across all generation phases.
- Nether portal round trips at aliases and canonical positions.
- End portal and End dimension behavior staying vanilla.
- Natural spawning near Nether tile edges.
- Structures/features near Nether tile edges.
