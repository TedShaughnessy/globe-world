# Dimension Tiling Plan

Goal: tiling is dimension-specific, not global.

- Overworld uses `mode` and `tile_size`.
- Nether uses `nether_mode` and the effective Nether tile size.
- The End never tiles.

## Nether Tile Size

`nether_one_eighth_overworld_size` is effective only when the Overworld tile size is cleanly divisible by 8.

Examples:

- Overworld `1024` chunks and one-eighth enabled -> Nether `128` chunks.
- Overworld `1025` chunks and one-eighth enabled -> Nether falls back to `1025` chunks.
- Nether disabled -> no Nether wrapping.

This keeps vanilla's 8:1 portal scale coherent only when the configured Overworld period supports it exactly.

## Current Implementation Slice

Implemented:

- `DimensionTiling` resolves the effective tiling context for Overworld, Nether, and End.
- `CoordUtil` now has dimension-aware overloads for wrapping, virtual positions, aliases, and wrapped distances.
- Runtime chunk/block/entity packet paths use level/player dimension context where available.
- Server chunk lookup, alias tickets, random ticks, spawning collection, tracking, block mutation, and worldgen region access use dimension-aware wrapping.
- Scheduled tick containers are tagged with their `ServerLevel` dimension when exposed by `ServerLevel`.
- Nether portal approximate exits are wrapped in the target dimension before portal search/creation.
- `/globeworld debug pos` reports the current dimension's effective tiling.

Still needs focused validation:

- Nether terrain/noise periodicity across all generation phases.
- Nether portal round trips at aliases and canonical positions.
- End portal and End dimension behavior staying vanilla.
- Natural spawning near Nether tile edges.
- Structures/features near Nether tile edges.

## Follow-Up Work

1. Add in-game Nether tests with a small tile and with `nether_one_eighth_overworld_size=true`.
2. Audit worldgen hooks that still rely on the scoped tiling context rather than an explicit `ServerLevel`.
3. Add debug output for whether one-eighth Nether sizing is requested versus effective.
4. Add a portal-specific test matrix:
   - Overworld canonical -> Nether canonical.
   - Overworld alias -> Nether canonical.
   - Nether canonical -> Overworld canonical.
   - Nether alias -> Overworld canonical.
5. Confirm End chunks, block changes, ticks, entities, and portals are not wrapped.
