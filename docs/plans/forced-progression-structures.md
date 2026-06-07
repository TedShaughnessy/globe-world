# Forced Progression Structures

This plan tracks remaining audit and diagnostics work for progression-critical
structures in wrapped finite tiles. Durable implemented behavior lives in
`docs/mod-mechanics/worldgen.md`.

## Current Behavior

Globe World can force exactly one missing canonical start for each enabled
progression structure type:

- Overworld stronghold, for End portal access.
- Nether fortress, for blaze rods, wither skeletons, nether wart, soul sand,
  and netherite upgrade smithing templates.

Forcing is a missing-structure fallback. If vanilla already creates a canonical
start for a type, Globe World does not add a duplicate forced start. Forced
starts are durable canonical world state, not alias-only views, and are created
during `STRUCTURE_STARTS` before reference generation.

The Nether path deliberately avoids a separate forced bastion. The forced
fortress contains the critical Nether progression affordances directly:

- tiny tiles use a fitted `CastleStalkRoom` for wither skeleton spawn space plus
  nether wart and soul sand,
- tiny tiles use a fitted `MonsterThrone` for the blaze spawner,
- every forced fortress path includes a small canonical chest containing one
  netherite upgrade smithing template,
- larger tiles use vanilla fortress generation and may cross tile borders
  through the existing toroidal structure and spillover paths.

The existing End portal fallback remains the emergency path when forced
strongholds are disabled, unavailable, or fail validation.

## User Settings

Independent settings are saved in `TilingSettings`:

- `force_missing_stronghold`
- `force_missing_nether_fortress`

The settings mean "force one if missing", not "always create one". Runtime
logic respects vanilla `generateStructures`; when structure generation is
disabled, forced starts are suppressed rather than silently overriding the
vanilla world option.

Current UI/default behavior:

- Stronghold: enabled when Overworld tiling is enabled and the Overworld tile
  is at most 256 chunks wide.
- Nether fortress: enabled when Nether tiling is enabled and the effective
  Nether tile is at most 256 chunks wide.
- The create-world UI exposes the toggles in a "Progression Structures"
  section, and the in-game settings screen shows them read-only.

## Implemented

- Overworld strongholds are wired into `STRUCTURE_STARTS`.
- Vanilla runs first. Globe World forces a stronghold only when the saved
  stronghold setting is enabled, Overworld tiling is enabled, vanilla structure
  generation is enabled, and no raw vanilla stronghold ring candidate is already
  inside the canonical tile.
- The forced stronghold start is deterministic from world seed and tile size,
  biased toward a canonical edge band, and saved through the normal structure
  manager path so reference generation and later lookups see durable canonical
  world state.
- Tiles of 32 chunks or smaller force only the vanilla portal room piece as the
  stronghold start. The one-piece fallback is shifted inward when needed so the
  portal room itself fits inside the canonical block tile.
- Eye of Ender targeting prefers vanilla canonical strongholds, then a
  validated forced stronghold target, then the emergency fallback portal.
- Nether fortresses are wired into `STRUCTURE_STARTS`.
- Vanilla runs first. Globe World forces a Nether fortress only when the saved
  fortress setting is enabled, Nether tiling is enabled, vanilla structure
  generation is enabled, and no raw random-spread fortress candidate is already
  inside the canonical Nether tile.
- Forced Nether fortresses are deterministic from world seed and effective
  Nether tile size. Tiles of 32 chunks or smaller choose an interior chunk and
  save fitted essential pieces. Larger tiles use edge-biased chunks and vanilla
  fortress generation so starts may cross tile borders through the existing
  toroidal worldgen paths.

## Remaining Work

- Add shared diagnostics for forced progression structures.
- Report selected forced chunks, whether a start has been generated and saved,
  and whether a tiny or vanilla layout was used.
- Validate that the forced fortress chest, blaze spawner, nether wart, soul
  sand, and wither skeleton spawn space persist after save/reload.
- Audit structure mob spawn overrides and locate-style queries near aliases.

Candidate commands:

- `/globeworld progression_structures`
- `/globeworld progression_structures validate`

Validation may load or generate `STRUCTURE_STARTS` chunks and should warn the
user, like the existing End portal validation command.

## Risks

- Forced structures may fail their own biome, terrain, or layout checks if
  forced too late or with the wrong generation context.
- Structure pieces may place lava, chests, spawners, mobs, post-processing
  marks, or block entities through paths not fully covered by current
  worldgen-spillover hooks.
- Progression kits are less vanilla-shaped than pure structure generation and
  should stay minimal, canonical, and diagnostic-visible.
- Locate-style queries, Eye of Ender targeting, structure mob spawn overrides,
  and saved references may need additional canonical/alias auditing.
- A fallback in `generateStructures=false` worlds changes vanilla semantics, so
  that choice should remain explicit and documented before changing it.

## Validation

Manual validation should cover at least:

- Create-world UI toggles persist into the saved world settings.
- Overworld tiling enabled with a seed/tile that naturally has no canonical
  stronghold.
- Nether tiling enabled with a tiny effective tile and a seed that naturally has
  no canonical fortress.
- A seed/tile where vanilla already has a canonical stronghold or fortress,
  confirming no forced duplicate is added for that structure type.
- Forced stronghold starts are near the canonical tile edge when the tile is
  large enough.
- Save/reload, then revisit forced structures and confirm the same canonical
  blocks, portal frames, spawners, chests, loot tables, and references persist.
- Walk to an alias of a progression chest, spawner, or nether wart patch and
  confirm it resolves to the same canonical content.
- Eye of Ender behavior for forced strongholds and for the existing fallback
  portal path.
- `/locate` or equivalent structure lookup behavior near aliases.
- Blaze spawning from spawners and wither skeleton spawning inside fortress
  bounding boxes.
- Nether wart, soul sand, and a netherite upgrade smithing template can be
  collected from the canonical forced fortress path.
- Larger forced fortress pieces can cross both X and Z tile edges.

## Related Files

- `mod-fabric/src/main/java/globe/world/config/TilingSettings.java`
- `mod-fabric/src/main/java/globe/world/config/GlobeConfig.java`
- `mod-fabric/src/client/java/globe/world/client/GlobeWorldSettingsControls.java`
- `mod-fabric/src/main/java/globe/world/util/ForcedProgressionStructures.java`
- `mod-fabric/src/main/java/globe/world/util/ForcedProgressionStructurePieces.java`
- `mod-fabric/src/main/java/globe/world/util/ForcedFortressProgressionChestPiece.java`
- `mod-fabric/src/main/java/globe/world/mixin/ChunkStatusTasksMixin.java`
