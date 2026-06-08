# Nether Size and Portal Scale Options

## Goal

Let Globe World worlds choose two independent Nether settings at world creation
time:

- Effective Nether tile size.
- Nether portal coordinate ratio.

This lets users make combinations that vanilla does not support:

- Same-size Nether tile with `1:8` fast travel.
- Small Nether tile with `1:1` travel.
- Larger-than-Overworld Nether tile.
- Reverse portal ratios where Overworld travel is effectively faster than
  Nether travel.

## Non-Goals

- Do not preserve backward compatibility for `nether_one_eighth_overworld_size`.
  Replace it with the new settings.
- Do not support runtime mutation of these settings. Treat them like the
  existing tile size and topology settings: chosen at world creation, then
  fixed.
- Do not change End portals or End dimension behavior.
- Do not support arbitrary ratios in the first UI. Use a curated set of ratios
  and tile-size presets that can be validated cleanly.

## Config Model

Replace `TilingSettings.netherOneEighthOverworldSize` with two settings:

```java
int netherTileSize
int netherPortalScaleNumerator
int netherPortalScaleDenominator
```

`netherTileSize` is the effective Nether tile size in chunks. It is independent
from the Overworld tile size.

`netherPortalScaleNumerator / netherPortalScaleDenominator` is the multiplier
applied to X/Z coordinates when traveling from Nether to Overworld:

```java
overworldCoordinate = netherCoordinate * numerator / denominator
```

Examples:

- `1 / 1`: 1 Nether block maps to 1 Overworld block.
- `2 / 1`: 1 Nether block maps to 2 Overworld blocks.
- `4 / 1`: 1 Nether block maps to 4 Overworld blocks.
- `8 / 1`: vanilla-style behavior, 1 Nether block maps to 8 Overworld blocks.
- `1 / 2`: 2 Nether blocks map to 1 Overworld block.
- `1 / 4`: 4 Nether blocks map to 1 Overworld block.
- `1 / 8`: 8 Nether blocks map to 1 Overworld block.

Store the ratio as two positive integers rather than a `double` so UI labels,
config files, and portal math stay exact.

Sanitize the ratio to an allowed preset pair. Since backward compatibility is
not required, remove codec support for the old boolean field.

## Tile Size Rules

The effective Nether tile size should no longer be derived from the portal
ratio. It should be stored directly:

```java
effectiveNetherTileSize = max(1, netherTileSize)
```

This intentionally permits:

- Nether tile smaller than Overworld.
- Nether tile same size as Overworld.
- Nether tile larger than Overworld.

The UI should offer common relative presets based on the current Overworld tile
size:

- `1/8 size`
- `1/4 size`
- `1/2 size`
- `Same size`
- `2x size`
- `4x size`

Only show or enable relative presets whose computed chunk size is valid for the
current mode. Custom mode may also expose a numeric Nether tile-size field, using
the same validation as the Overworld custom tile-size field.

Simple mode should keep both Overworld and Nether tile sizes at or above the
simple-mode minimum. With the intended minimum of `8` chunks:

- Overworld `8` chunks: smallest Nether option is `8` chunks.
- Overworld `16` chunks: smallest Nether option is `8` chunks.
- Overworld `32` chunks: `1/4 size` is the smallest relative option.
- Overworld `64+` chunks: all shrink presets can be valid when divisible.

## Portal Ratio Rules

Portal ratio is independent from Nether tile size. The UI should expose:

- `1:8 reverse`
- `1:4 reverse`
- `1:2 reverse`
- `1:1`
- `1:2`
- `1:4`
- `1:8 vanilla`

Where the label `1:N` means "1 Nether block maps to N Overworld blocks", and
`1:N reverse` means "N Nether blocks map to 1 Overworld block".

Internally:

| UI label | numerator | denominator |
| --- | ---: | ---: |
| `1:8 reverse` | `1` | `8` |
| `1:4 reverse` | `1` | `4` |
| `1:2 reverse` | `1` | `2` |
| `1:1` | `1` | `1` |
| `1:2` | `2` | `1` |
| `1:4` | `4` | `1` |
| `1:8 vanilla` | `8` | `1` |

Default to `8 / 1` for vanilla-style Nether travel.

## Portal Travel Behavior

Add a small helper that returns Globe World's configured multiplier for
Overworld/Nether travel:

```java
public static double netherPortalTeleportationScale(ServerLevel from, ServerLevel to)
```

Expected values:

- Overworld -> Nether: `denominator / numerator`
- Nether -> Overworld: `numerator / denominator`
- Other dimension pairs: vanilla `DimensionType.getTeleportationScale(...)`

Update `NetherPortalBlockMixin.canonicalizeTargetApproximateExit` to use this
helper instead of vanilla's `DimensionType.getTeleportationScale(...)`.

The existing canonicalization flow should remain:

1. Start from vanilla's approximate target coordinate.
2. Convert back to source coordinate using the configured scale.
3. Wrap source X/Z in the source dimension.
4. Reapply the configured scale.
5. Clamp and wrap the target position in the target dimension.

This keeps alias copies of the same portal from producing separate target
portals.

## UI Plan

Replace the current combined `NetherGlobeMode` choices:

- `Disabled`
- `Same size`
- `1/8 size`

with separate controls:

### Nether Size

- `Disabled`
- `1/8 size`
- `1/4 size`
- `1/2 size`
- `Same size`
- `2x size`
- `4x size`

Custom mode can additionally allow direct numeric chunk entry.

### Portal Ratio

- `1:8 reverse`
- `1:4 reverse`
- `1:2 reverse`
- `1:1`
- `1:2`
- `1:4`
- `1:8 vanilla`

When a Nether size is selected:

- Enable Nether tiling.
- Set `netherTileSize` to the selected or entered value.
- Reset `nether_terrain_mode` to `auto`, matching current behavior when the
  effective Nether tile changes.
- Recompute `force_missing_nether_fortress` default from the new effective
  Nether tile size.

When a portal ratio is selected:

- Set `netherPortalScaleNumerator` and `netherPortalScaleDenominator`.
- Do not change Nether tile size.
- Do not change Nether terrain mode.

The Nether summary text should include tile size and portal ratio, for example:

```text
Nether tile: 16.4 km, 1,024 chunks, edge blend terrain, portal 1:8
```

## Files To Change

- `mod-fabric/src/main/java/globe/world/config/TilingSettings.java`
  - Replace the boolean record field with `int netherTileSize`,
    `int netherPortalScaleNumerator`, and
    `int netherPortalScaleDenominator`.
  - Replace `supportsNetherOneEighthOverworldSize`,
    `effectiveNetherOneEighthOverworldSize`, and
    `withNetherOneEighthOverworldSize` with independent Nether tile-size and
    portal-ratio methods.
  - Update `netherTileSize()` and sanitization/defaulting.
- `mod-fabric/src/main/java/globe/world/config/GlobeConfig.java`
  - Expose `netherPortalScaleNumerator()`,
    `netherPortalScaleDenominator()`, and a helper/label for the effective
    portal ratio.
  - Remove one-eighth accessors.
- `mod-fabric/src/main/java/globe/world/mixin/NetherPortalBlockMixin.java`
  - Use configured scale for Overworld/Nether portal destination math.
- `mod-fabric/src/client/java/globe/world/client/GlobeWorldSettingsControls.java`
  - Replace `NetherGlobeMode` with separate Nether-size and portal-ratio
    controls.
  - Update labels and summary copy.
- `mod-fabric/src/main/java/globe/world/GlobeDebugCommands.java`
  - Print `nether_tile_size` and `portal_scale` instead of one-eighth
    requested/effective flags.
- `docs/mod-mechanics/topology.md`
  - After implementation, move the durable explanation here and remove this
    plan or mark it retired.
- `docs/mod-mechanics/client.md`
  - Update create-world UI wording after the UI change lands.

## Validation

Ask the user to run:

```sh
./gradlew build
```

Manual play-test matrix:

- Same-size Nether tile with `1:8` portal travel.
- Smaller Nether tile with `1:1` portal travel.
- Larger Nether tile with `1:1` portal travel.
- Larger Nether tile with a reverse portal ratio.
- `1:2`, `1:4`, and `1:8` forward portal ratios.
- `1:2`, `1:4`, and `1:8` reverse portal ratios.
- Portal entry from canonical coordinates.
- Portal entry from an alias near each X/Z tile edge.
- Round trip from Overworld -> Nether -> Overworld returns to the same
  canonical portal identity.
- New target portal creation happens once per canonical source portal, not once
  per alias copy.

## Implementation Order

1. Replace the config field and helper methods in `TilingSettings`.
2. Update `DimensionTiling` callers indirectly by keeping `netherTileSize()`
   as the public tile-size source.
3. Add the configured portal-ratio helper and update `NetherPortalBlockMixin`.
4. Split create-world Nether controls into size and portal-ratio controls.
5. Update debug command output.
6. Update docs and remove stale one-eighth language.
7. Ask the user to run the Gradle build and then perform the manual portal
   round-trip tests.
