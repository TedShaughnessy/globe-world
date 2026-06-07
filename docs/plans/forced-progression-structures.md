# Forced Progression Structures

This is the implementation plan for making progression-critical structures
available when a wrapped finite tile is too small to naturally contain them.

## Target Behavior

Globe World should be able to force exactly one missing canonical start for each
enabled progression structure type:

- Overworld stronghold, for End portal access.
- Nether fortress, for blaze rods, wither skeletons, and nether wart.
- Bastion remnant, for netherite upgrade smithing templates and bastion loot.

Forcing is a missing-structure fallback. If vanilla already creates a canonical
start for a type, Globe World must not add a duplicate forced start for that
type.

Forced starts are durable canonical world state, not alias-only views. Alias
starts are transient by design; progression structures must survive save/reload,
be reachable by later structure queries, and be placed before reference
generation so the existing toroidal structure-reference path can route
edge-crossing pieces back into canonical chunks.

The existing End portal fallback remains the emergency path when forced
strongholds are disabled, unavailable, or fail validation.

Because some progression content is not knowable before the relevant structure
pieces generate, the implementation should avoid trying to prove every vanilla
piece outcome ahead of time. Instead:

1. If a canonical structure start of that type already exists, do nothing for
   that type.
2. If no canonical start exists and Globe World forces one, attach a small
   deterministic canonical progression kit to the forced structure.

The kit is part of the forced fallback, not a general repair pass for vanilla
structures. This keeps existing vanilla starts untouched while making every
forced start actually useful for progression.

## User Settings

Independent settings are saved in `TilingSettings`:

- `force_missing_stronghold`
- `force_missing_nether_fortress`
- `force_missing_bastion`

Current UI/default scaffolding:

- Stronghold: enabled when Overworld tiling is enabled and the Overworld tile
  is at most 256 chunks wide.
- Nether fortress: enabled when Nether tiling is enabled and the effective
  Nether tile is at most 256 chunks wide.
- Bastion: enabled when Nether tiling is enabled and the effective Nether tile
  is at most 256 chunks wide.

The settings should mean "force one if missing", not "always create one".
Runtime logic still respects vanilla `generateStructures` in the first pass:
when structure generation is disabled, report that forcing is suppressed rather
than silently overriding the vanilla world option.

Implementation notes:

- Done: `TilingSettings` record, `CODEC`, constructors, `sanitized()`, and
  `with...` helpers include the saved fields.
- Done: `GlobeConfig` exposes accessors for each setting.
- Done: old worlds decode missing fields from tile-size-derived defaults.
- The toggles are stored as saved policy, but future runtime behavior should
  only act on them when the matching tiling mode is enabled.

## Create-World UI

A "Progression Structures" section exists in the Globe World create-game tab
using the existing `GlobeWorldSettingsControls` row system. The same section is
shown read-only in the in-game options screen because these are world-generation
settings.

Controls:

- Stronghold: on/off toggle, visible when Overworld tiling is enabled.
- Nether Fortress: on/off toggle, visible when Nether tiling is enabled.
- Bastion Remnant: on/off toggle, visible when Nether tiling is enabled.

Recommended placement in the tab:

- Put the section after Nether topology/curvature and before day/night controls.
- Use the existing scrollable tab, checkbox, tooltip, refresh, and editability
  patterns.
- Disable controls in the pause-menu read-only state, following the existing
  settings controls.

Suggested labels:

- `Force Missing Stronghold`
- `Force Missing Fortress`
- `Force Missing Bastion`

Suggested tooltips:

- Stronghold: "Adds one canonical stronghold only if the wrapped Overworld has
  no canonical stronghold." The implemented tooltip also notes the existing Eye
  of Ender emergency End portal fallback.
- Fortress: "Adds one canonical fortress only if the wrapped Nether has no
  canonical fortress."
- Bastion: "Adds one canonical bastion only if the wrapped Nether has no
  canonical bastion."

Implementation files:

- `mod-fabric/src/main/java/globe/world/config/TilingSettings.java`
- `mod-fabric/src/main/java/globe/world/config/GlobeConfig.java`
- `mod-fabric/src/client/java/globe/world/client/GlobeWorldSettingsControls.java`
- `mod-fabric/src/client/java/globe/world/client/GlobeWorldCreateState.java`
- `mod-fabric/src/client/java/globe/world/client/GlobeWorldTab.java`
- `mod-fabric/src/main/java/globe/world/util/ForcedProgressionStructures.java`
- `mod-fabric/src/main/java/globe/world/mixin/ChunkStatusTasksMixin.java`

## Implemented So Far

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
  stronghold start, preventing full stronghold graphs from sprawling repeatedly
  across tiny wrapped tiles. The one-piece fallback is shifted inward when
  needed so the portal room itself fits inside the canonical block tile instead
  of relying on cross-boundary spillover for the critical room. Larger forced
  strongholds still use the vanilla stronghold layout.
- Eye of Ender targeting prefers vanilla canonical strongholds, then a
  validated forced stronghold target, then the emergency fallback portal. A
  previously saved fallback portal does not mask a later-valid stronghold path.

Still planned:

- Shared diagnostics for forced progression structures.
- Nether fortress and bastion forced starts.
- Deterministic progression kits for forced starts.

## Availability Checks

Create a shared server-side availability utility, tentatively
`ProgressionStructureAvailability`, that can report one structure type at a
time and a combined summary for diagnostics.

Availability is based on canonical structure starts, not on proving every
progression item before the world generates. If a canonical start already
exists for a type, forcing does nothing for that type. If a forced start is
created, Globe World adds the required progression kit to that forced start.

For each enabled type:

- Confirm the current dimension is tiled.
- Confirm vanilla structure generation is enabled.
- Resolve the relevant structure holder from the registry.
- Inspect generator-state placements for that holder.
- Count raw candidates, canonical candidates, and wrapped alias candidates.
- Optionally validate canonical starts by loading/generating
  `STRUCTURE_STARTS` chunks, matching the caution already used by
  `/globeworld end_portal validate`.
- Return one of:
  - disabled by setting,
  - disabled by dimension,
  - suppressed because structures are disabled,
  - vanilla canonical start present,
  - forced start required,
  - forced start present.

Strongholds can reuse concepts from `EndPortalAvailability`, but should not
pretend ring placement is the same as Nether random-spread placement.

## Placement Policy

All forced starts must be deterministic from world seed, dimension, structure
type, tile settings, and an implementation salt. The same world should choose
the same fallback chunks after save/reload.

### Stronghold

Place the forced stronghold start near a canonical tile edge.

Reasoning:

- Strongholds are large and benefit from exercising toroidal edge handling.
- A near-edge stronghold makes the wrapped world feel continuous instead of
  hiding all progression content near the center.
- Eye of Ender behavior should be tested against a target that may be closest
  through a wrapped alias.

Policy:

- Prefer an edge band, for example the outer 20-25% of canonical chunks on one
  X or Z side.
- Pick the side and offset deterministically.
- Keep enough inset to avoid trivially clipping the portal room out of the tile
  on medium tiles. For the tiny portal-room-only fallback, favor reliable
  canonical placement of the room over edge-crossing stress.
- If the tile is too small for the inset, fall back to the best deterministic
  canonical chunk and report the degraded placement in diagnostics.

### Bastion Remnant

Place the forced bastion start near a canonical tile edge.

Reasoning:

- Bastions are large enough that edge placement is a useful stress test for
  jigsaw pieces, loot chests, and block entities.
- Bastions are not required for the main End route, so putting one near the
  edge is acceptable and makes the wrapped tile more interesting.

Policy:

- Use an edge band like strongholds.
- Add a canonical progression chest in or near the forced bastion footprint
  using ordinary saved block-entity state. The chest should contain or reference
  the vanilla netherite-upgrade-template reward path and should not duplicate
  across aliases.
- Avoid existing canonical Nether structures when possible.
- Avoid the forced fortress start if both Nether structures are missing.

### Nether Fortress

The forced fortress can go anywhere in the canonical Nether tile, but it should
avoid forced bastions and existing canonical structures.

Reasoning:

- Fortresses are progression-critical and should prioritize reliable generation
  over edge stress.
- They can still cross edges naturally if the chosen start or pieces are large,
  but edge placement is not required.

Policy:

- Add a canonical blaze spawner in or near the forced fortress footprint.
- Add a canonical chest containing nether wart and soul sand, or place a small
  nether wart patch on soul sand nearby. A chest is simpler and less likely to
  be overwritten by terrain or structure post-processing; a physical patch feels
  more vanilla if placement can be made reliable.
- Do not provide a blaze spawner item through loot. A placed blaze spawner block
  preserves the vanilla progression loop; a spawner item would create a
  non-vanilla survival reward.
- Try a deterministic list of candidate chunks across the tile.
- Score candidates by distance from existing canonical structures and the
  planned forced bastion.
- Prefer non-edge candidates when enough space exists.
- If every candidate conflicts on a tiny tile, choose the least-bad canonical
  candidate and report the conflict in diagnostics.

## Collision And Existing-Structure Avoidance

Before selecting forced starts, gather known canonical starts/references for
progression structures in the same dimension.

First pass collision rules:

- Never force a type that already has a canonical start.
- Do not place a forced Nether fortress on the same start chunk as a forced
  bastion.
- Prefer a minimum chunk distance between forced starts and existing canonical
  starts.
- On tiny tiles, allow degraded placement rather than failing progression, but
  expose it in diagnostics.

Later improvements can use bounding boxes after provisional start generation,
but the first pass can use start-chunk distance and validation output. Do not
try to avoid collisions across dimensions; Overworld stronghold placement and
Nether structure placement are independent.

## Structure Injection Hook

The likely hook is the `STRUCTURE_STARTS` phase, near vanilla
`ChunkGenerator.createStructures(...)`, because that is where starts become
owned by chunks.

Implementation shape:

- Let vanilla create starts first.
- For a canonical chunk that matches a selected forced fallback chunk, check
  whether that structure type is still missing.
- Build the vanilla `Structure.GenerationContext` for the forced structure and
  chosen source chunk.
- Call vanilla `Structure.generate(...)` or the closest mapped helper in 26.1.2.
- Store the resulting valid `StructureStart` on the canonical chunk.
- Ensure later `STRUCTURE_REFERENCES` sees the forced start so
  `ChunkGeneratorMixin.addToroidalStructureReferences(...)` can create wrapped
  references.

Avoid forcing at biome decoration time. By then references, structure mob spawn
overrides, locate behavior, and saved starts have already diverged from vanilla
expectations.

## Forced Progression Kits

Progression kits are attached only to structures that Globe World forced because
no canonical start existed. They are not used to repair vanilla-generated
starts, even if a vanilla start happens not to contain every progression
affordance.

Rules:

- Run after the forced structure's relevant canonical chunks are generated
  enough that the kit will not be overwritten by later terrain or structure
  placement.
- Persist a per-world record of kit positions so the kit is applied once and
  survives save/reload.
- Write only canonical block positions.
- Prefer positions inside a relevant structure bounding box. If no suitable
  piece exists, use a deterministic nearby canonical position and report that
  degraded placement in diagnostics.
- Do not create a kit for a structure type whose canonical start already existed
  before forcing.

Fortress kit:

- One blaze spawner with vanilla blaze spawn data.
- A chest containing nether wart and soul sand, or a small nether wart patch on
  soul sand if physical placement is reliable.
- Placement should be reachable and protected from immediate terrain overwrite
  by running after the destination chunks are ready.

Bastion kit:

- One canonical chest or loot-bearing container for netherite upgrade template
  access.
- Prefer the vanilla treasure-room reward path when possible. If a direct chest
  patch is used, its loot state must be canonical and one-time, like an ordinary
  generated chest.

This fallback is less vanilla-shaped than pure structure generation, so it
should be narrow, visible in diagnostics, and limited to progression content
rather than general loot enrichment.

## Stronghold And End Portal Interaction

Forced strongholds are the preferred End progression path when enabled and
valid.

Rules:

- If a canonical vanilla or forced stronghold is present, Eye of Ender targets
  it through normal or minimally wrapped structure lookup.
- If forced stronghold generation is enabled but no valid start can be created,
  use the existing `EndPortalFallback` behavior.
- If forced stronghold generation is disabled and no canonical vanilla
  stronghold exists, use the existing `EndPortalFallback` behavior.
- `/globeworld end_portal` reports whether End progression is provided by
  vanilla stronghold, forced stronghold, or fallback portal.

## Diagnostics And Commands

Add a debug command, or extend existing debug output, to report:

- enabled/disabled settings for each structure type,
- availability status,
- selected fallback chunks,
- selected progression kit positions,
- whether selected chunks are edge-biased or degraded,
- nearest existing canonical structures considered for avoidance,
- whether a forced start has been generated and saved,
- whether a progression kit was placed,
- validation summary for starts and references.

Candidate commands:

- `/globeworld progression_structures`
- `/globeworld progression_structures validate`

Validation may load or generate `STRUCTURE_STARTS` chunks and should warn the
user, like the existing End portal validation command.

## Implementation Phases

1. Settings and UI:
   Add saved booleans, create-world toggles, pause-menu read-only display, and
   networking/codec compatibility.

2. Availability and placement selection:
   Implement status reporting and deterministic fallback chunk selection without
   injecting starts yet. Add diagnostics.

3. Nether forced starts:
   Implement bastion and fortress forcing first because both are random-spread
   Nether structures. Validate collision avoidance and edge-biased bastion
   placement.

4. Nether progression kits:
   Add deterministic blaze-spawner/nether-wart and bastion-template kits to
   forced Nether starts.

5. Overworld forced stronghold:
   Done for the first pass: stronghold forcing is wired to worldgen and Eye of
   Ender targeting, with the fallback portal kept as the emergency path.

6. Documentation:
   Move durable behavior into `docs/mod-mechanics/worldgen.md` once implemented
   and keep this plan only for remaining audit work.

## Risks

- Strongholds, fortresses, or bastions may fail their own biome, terrain, or
  layout checks if forced too late or with the wrong generation context.
- Strongholds need special care because End progression currently depends on
  canonical ring candidates and a persisted fallback portal target.
- Structure pieces may place lava, chests, spawners, mobs, post-processing
  marks, or block entities through paths not fully covered by current
  worldgen-spillover hooks.
- Bastion loot chests and template availability must remain deterministic and
  should not duplicate across aliases.
- Progression kits are less vanilla-shaped than pure structure generation and
  should stay minimal, canonical, and diagnostic-visible.
- Locate-style queries, Eye of Ender targeting, structure mob spawn overrides,
  and saved references may need additional canonical/alias auditing.
- Multiple forced starts could duplicate progression rewards on tiny tiles; the
  first pass should guarantee at most one fallback start per required structure
  type.
- A fallback in `generateStructures=false` worlds changes vanilla semantics, so
  that choice should be explicit and documented before changing it.

## Validation

Manual validation should cover at least:

- Create-world UI toggles persist into the saved world settings.
- Overworld tiling enabled with a seed/tile that naturally has no canonical
  stronghold.
- Nether tiling enabled with a tiny effective tile and a seed that naturally has
  no canonical fortress and no canonical bastion.
- A seed/tile where vanilla already has a canonical stronghold, fortress, or
  bastion, confirming no forced duplicate or progression kit is added for that
  structure type.
- Forced stronghold and bastion starts are near the canonical tile edge when the
  tile is large enough.
- Forced fortress avoids the forced bastion and existing canonical structures
  when the tile is large enough.
- Save/reload, then revisit forced structures and confirm the same canonical
  blocks, portal frames, spawners, chests, loot tables, and references persist.
- Walk to an alias of a progression-kit spawner/chest/nether-wart patch and
  confirm it resolves to the same canonical content.
- Eye of Ender behavior for forced strongholds and for the existing fallback
  portal path.
- `/locate` or equivalent structure lookup behavior near aliases.
- Blaze spawning from spawners and wither skeleton spawning inside fortress
  bounding boxes.
- Nether wart can be collected from the canonical fortress path.
- Netherite upgrade template availability from bastion loot.
- Forced structure pieces crossing both X and Z tile edges.

## Related Files

- `mod-fabric/src/main/java/globe/world/config/TilingSettings.java`
- `mod-fabric/src/main/java/globe/world/config/GlobeConfig.java`
- `mod-fabric/src/client/java/globe/world/client/GlobeWorldSettingsControls.java`
- `mod-fabric/src/main/java/globe/world/mixin/ChunkGeneratorMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/StructurePlacementMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/StructureStartMixin.java`
- `mod-fabric/src/main/java/globe/world/util/StructurePlacementShifts.java`
- `mod-fabric/src/main/java/globe/world/util/EndPortalAvailability.java`
- `mod-fabric/src/main/java/globe/world/util/EndPortalFallback.java`
- `docs/mod-mechanics/worldgen.md`
- `docs/vanilla-mechanics/structure-edge-generation.md`
