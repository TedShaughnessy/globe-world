# Worldgen

## What

World generation must produce canonical chunks whose contents line up across
tile edges. Reads and writes during generation also need to respect the canonical
tile so features and structures can cross a seam without creating independent
alias state.

## Why

Runtime wrapping can make an already-generated finite tile behave continuous,
but it cannot make terrain, biomes, caves, surfaces, or structures visually
periodic after the fact. The generator's sample space needs a periodic strategy,
and worldgen side effects must write canonical state exactly once.

## Terrain Modes

`TerrainMode` selects a generation strategy. `AUTO` derives the method from
tile size; custom world creation can also save an explicit method per wrapped
dimension:

- `COMPACT_TORUS`: tiny/stylized tiles.
- `EDGE_BLEND`: arbitrary medium/large sizes where preserving vanilla scale in
  the interior matters.
- `PERIODIC_LATTICE`: clean sizes where true periodic lattice noise can close
  the tile.

The automatic policy uses compact torus for small tiles, periodic lattice for
clean large multiples, and edge blend for awkward medium/large sizes. Changing
tile size resets saved explicit terrain methods back to `AUTO`.

Experimental hex topology currently forces/resolves terrain to `EDGE_BLEND`,
but this first run does not implement true six-edge terrain continuity. Hex
worldgen ownership helpers canonicalize chunks and block writes through the
geometry boundary, while terrain, biome, cave, feature, and structure seams may
still be visibly discontinuous.

## Seed Preflight

`TopologySettings.avoid_water_only_seeds` is a create-time heuristic for random
small wrapped Overworlds. When Overworld wrapping is enabled, the setting
defaults on; when wrapping is disabled, it defaults off. The field is optional
in the saved schema and falls back through that same rule if omitted. Changing
Overworld wrapping mode or tile size refreshes the default alongside the forced
progression-structure defaults.

During client world creation, `CreateWorldScreenMixin` gives
`GlobeSeedPreflight` the final baked registry layers and the vanilla
`WorldDataAndGenSettings` before the `CreateWorldCallback` opens a world folder
or starts a server. The preflight only runs when the saved topology enables it
and the vanilla seed text is empty. If the player types any seed text, including
a numeric seed or a named seed, Globe World leaves the parsed vanilla seed
unchanged.

For each candidate seed, the preflight resolves the final Overworld
`LevelStem`, skips debug and flat generators, builds a biome climate sampler for
noise-based generators, and samples the canonical tile bounds at a coarse block
grid. Ocean, deep ocean, and river biomes count as water-like; beaches count as
land-adjacent and accept the seed. The first candidate is the vanilla random
seed already present in `WorldOptions`; later candidates use
`WorldOptions.randomSeed()`. If all 32 attempts still appear water-only, Globe
World logs a warning and creates the world with the last tested seed. Large
tiles whose sample grid would exceed the hard cap estimate that grid before
allocating sample coordinates, skip the heuristic, and keep the original seed.
This keeps very large random world creation from pausing on client-side
preflight work.

## Forced Progression Structures

`GlobeSettings.topology()` saves two world-generation policy toggles:
`force_missing_stronghold` and `force_missing_nether_fortress`. The stronghold
toggle is wired for the Overworld, and the fortress toggle is wired for the
Nether. The settings mean "force one if missing", not "always create one":
vanilla structure generation runs first, and Globe World only creates a forced
canonical start when vanilla has no raw candidate of that type inside the
canonical tile. Forced starts are durable canonical world state saved during
`STRUCTURE_STARTS`; aliases remain views of that state rather than separate
structure owners. Runtime logic also respects vanilla `generateStructures`, so
worlds with structure generation disabled do not get forced progression starts.

For strongholds, Globe World adds exactly one canonical stronghold start at
chunk 0,0 when tiling and vanilla structures are enabled and no raw stronghold
ring candidate exists inside the canonical Overworld tile. Tiny tiles up to 32
chunks use a saved stronghold start containing only the vanilla portal room
piece, avoiding a full stronghold graph that would sprawl across the tile many
times. Larger forced strongholds use the vanilla stronghold layout.

The Nether fortress toggle uses the same structure-start phase for the Nether:
vanilla runs first, then Globe World selects the nearest raw random-spread
fortress candidate inside the canonical Nether tile and ensures that selected
start contains the progression-critical fortress affordances: a `CastleStalkRoom`
for nether wart and soul sand, a `MonsterThrone` for a blaze spawner, and a
small canonical upgrade chest containing one netherite upgrade smithing
template. If vanilla's generated start lacks the stalk room or throne, Globe
World appends fitted supplemental fortress pieces to the same saved start rather
than forcing a duplicate fortress. If no raw random-spread fortress candidate
exists inside the canonical Nether tile, Globe World adds one deterministic
canonical fortress start instead. Tiny Nether tiles up to 32 chunks use fitted
essential fortress pieces that stay inside the tile. Larger forced Nether
fortresses use vanilla structure generation so existing seam spillover and
shifted-reference handling can let pieces cross tile borders, then supplement
the forced start if needed. The upgrade chest prefers the space behind a
`CastleStalkRoom` staircase and falls back to the start chunk if no stalk room is
present. The settings default on for matching dimensions whose effective tile
size is at most 256 chunks and off for larger tiles.

## Implementation

Terrain and biome hooks route many X/Z-dependent samples through periodic noise
utilities or terrain-mode-aware sampling. Positional random factories are wrapped
where the caller's coordinate unit is known. Generator phases that rely on raw
ambient `CoordUtil` calls run inside scoped dimension tiling contexts; async
biome/noise work captures the caller's dimension context and restores it on the
worker thread. For extreme periodic-lattice tiles, octave cell counts that
exceed Java's integer range are capped to the largest representable cell period
and the sample scale is adjusted to match that cap, preventing overflow during
chunk generation.

Alias `LevelChunk.postProcessGeneration` is cancelled and queued
post-processing offsets are cleared so alias neighbor-shape fixes do not write
through wrapped `Level.setBlock` into canonical storage.

`GenerationWindow` is the shared worldgen helper for bounded region access at
tile edges. It gives `WorldGenRegionMixin` one vocabulary for canonical block
reads, physical-cache alias chunk lookup, toroidal write-radius checks, physical
cache availability, and write classification. Square worlds use independent
X/Z periods; hex worlds resolve cache aliases and canonical destinations through
`TopologyContext` and `TileGeometry`. It does not call live
`ServerLevel.getChunk(...)`, mutate chunks directly, or replace the terrain and
noise periodicity hooks. Manual seam testing after the extraction confirmed the
helper preserves the existing ownership model and does not introduce durable
alias-origin generation.

Worldgen spillover handles block writes that wrap across X/Z tile edges during
generation. `WorldGenRegion` write hooks classify each write through
`GenerationWindow` and enqueue the wrapped canonical block state into
server-owned transient spillover state keyed by dimension and canonical chunk.
When the canonical destination is visible in the bounded `WorldGenRegion`
cache, vanilla first accepts the canonical write and the queued spillover entry
records the block state observed at the destination. Canonical chunks apply
queued spillover during biome decoration, post-processing, and before chunk
packet serialization, but replay a guarded queued write only if the destination
still matches the observed state. This prevents stale leaf, grass, or other
decoration writes from overwriting trunks and other blocks placed by the
destination chunk after the spillover write was queued. When an external
worldgen provider such as Distant Horizons does not expose the opposite-edge
chunk in the active `WorldGenRegion` cache, Globe World queues the wrapped write
without directly touching the missing chunk; those writes replay later without
an observed-state guard because the destination state could not be sampled
safely. Level close and server stop discard any remaining queues and warn if
writes were abandoned; spillover queues are runtime bookkeeping and are not
saved world data.

Worldgen diagnostics on the `WORLDGEN` channel use `GW_WORLDGEN_WINDOW` for
generation-window decisions and `GW_WORLDGEN_SPILLOVER` for queue lifecycle.
Window logs distinguish wrapped visible writes, wrapped unobserved writes,
radius-denied writes, and unavailable no-load chunk lookups. Spillover enqueue
logs identify whether the queued write is guarded; existing apply, skip, stale,
and cleanup events keep the same event names.
The same channel uses `GW_SEED_PREFLIGHT` for water-only seed sampling details,
including sample counts, rejected seeds, and the first non-water biome found for
accepted candidates.

Structure edge handling stores bounded virtual source keys on canonical target
chunks during reference generation, resolves them during biome decoration, and
places vanilla starts with a whole-tile chunk-box shift. Alias starts are
transient worldgen data and are not durable owners; the saved start remains on
the canonical source chunk. The virtual reference key is placement metadata used
to recover the required shift, not an alias-owned start. Alias biome decoration
and reference generation are skipped without leaking their tiling context into
surrounding generation work. Reference generation checks both the shifted start
bounding box and individual shifted piece boxes before saving a source key. This
matters for forced composite starts such as the tiny Nether fortress: vanilla
structure pieces clip themselves to the target chunk box, so every ordinary
chunk overlapped by a forced piece needs a reference even when the structure
does not cross a tile boundary. During biome decoration, fortress and stronghold
placement also has a bounded fallback for an empty reference set: it scans the
same nearby source-start radius and queues the same placement shift without
making alias chunks durable owners. This catches already-missing progression
references that would otherwise leave a hard chunk cutoff.

Non-`setBlock` worldgen side effects are covered by separate hooks or documented
limits. The source-backed vanilla classification lives in
[Worldgen Direct Mutation Matrix](../vanilla-mechanics/worldgen-direct-mutation-matrix.md).
`WorldGenRegionMixin` and `BulkSectionAccessMixin` apply the canonical
generation window only to exact vanilla `WorldGenRegion` instances. Synthetic
worldgen regions from external providers are left in their own coordinate space:
Distant Horizons, for example, builds a finite raw-position batch map and can
crash if an edge-neighbor read is rewritten to the canonical opposite edge.
`BulkSectionAccessMixin` is still needed for vanilla ore placement because that
path bypasses `WorldGenRegion.setBlock(...)`. Same-chunk terrain, surface,
carver, and retrogen writes remain canonical-owner operations and do not need a
global `ChunkAccess` or `LevelChunkSection` hook. Block entity creation, POI
updates, and post-processing marks are covered for visible writes because the
canonical write still flows through vanilla `WorldGenRegion.setBlock(...)`;
unobserved spillover replays only the block state through
`ChunkAccess.setBlockState(...)`, so attached block-entity NBT, scheduled block
or fluid ticks, and POI side effects from the unobserved provider path are not
fully reconstructed. `WorldGenRegion` post-processing positions are
canonicalized before chunk marking. `ServerLevelEntityMixin` canonicalizes
worldgen chunk entities before server storage.

Forced missing Overworld strongholds are created during `STRUCTURE_STARTS`,
immediately after vanilla `ChunkGenerator.createStructures(...)` finishes for a
canonical chunk. `ForcedProgressionStructures` resolves the vanilla stronghold
holder, inspects concentric-ring placements for canonical raw candidates, and
only acts when none exist. The forced chunk is canonical chunk 0,0 and is saved
through `StructureManager.setStartForStructure(...)` so later reference
generation, biome decoration, and structure lookups treat it as ordinary
canonical world state. On tiles of 32 chunks or smaller, the forced start is a
single vanilla
`StrongholdPieces.PortalRoom`; Globe World shifts that piece inward when needed
so the portal room's bounding box fits inside the canonical block tile instead
of depending on seam spillover for the critical progression room. On larger
small tiles it is generated through vanilla `Structure.generate(...)`.

Nether fortress progression is also handled during `STRUCTURE_STARTS`,
immediately after vanilla structure creation for canonical Nether chunks.
`ForcedProgressionStructures` resolves the vanilla fortress holder and inspects
random-spread placements by grid cell for canonical raw candidates. If one or
more canonical candidates exist, it selects the candidate nearest canonical
chunk 0,0 and uses that start as the canonical fortress owner. Nether fortress
piece generation does not guarantee a stalk room or monster throne, so Globe
World inspects the selected start after vanilla generation and appends
supplemental fitted fortress pieces when either the wart room or blaze throne is
missing. If no canonical candidate exists, the forced chunk is deterministic from
the world seed, effective Nether tile size, and a fortress salt. Tiles of 32
chunks or smaller choose an interior forced chunk and save fitted minimal starts
so the essential pieces do not depend on crossing a tile border. The tiny layout
creates the stalk room and blaze throne at vanilla forward-connection
coordinates, then fits them as a group so the throne stays connected instead of
being moved into the room. It also includes explicit wart-bed patch pieces that
guarantee soul sand and nether wart in the stalk room. Larger forced tiles choose
an edge-biased chunk and call vanilla `Structure.generate(...)`, preserving the
normal structure layout and allowing border crossing through the existing
toroidal structure placement and spillover paths; if that generated layout lacks
critical fortress pieces, it receives the same supplemental pieces. The upgrade
chest is placed behind a `CastleStalkRoom` staircase when that piece is available,
with a fallback at the start chunk if no stalk room can host it, so Globe World
does not need a separate forced bastion fallback.

End portal progression uses canonical stronghold ownership instead of alias
structure lookup. In the Overworld, `EndPortalAvailability` inspects vanilla
stronghold concentric-ring positions and treats a raw ring candidate inside the
canonical tile as the preferred durable stronghold. Wrapped alias candidates are
reported for diagnostics but do not count as owned world state. When no
canonical vanilla candidate exists and `force_missing_stronghold` can provide a
valid forced start, `EnderEyeItemMixin` intercepts thrown Eyes of Ender and
signals the deterministic forced stronghold target instead of using the fallback
portal. That forced target is validated by generating the forced chunk to
`STRUCTURE_STARTS`; if validation fails, the emergency fallback remains
available.

When tiling is enabled and neither vanilla nor forced stronghold progression is
available, or when structure generation is disabled, `EnderEyeItemMixin`
replaces a thrown Eye of Ender with a fallback path: `EndPortalFallback` chooses
and persists one canonical position centered on the throwing player, repairs a
5x5 End portal frame with a deterministic random subset of eyes already
inserted, then spawns a normal Eye of Ender. The eye entity and its flight
target stay in canonical server coordinates so entity storage canonicalization
cannot desynchronize the projectile from its target; entity packets still render
the eye through the nearest visual alias for the throwing player. The fallback
path preserves vanilla item use, stat, sound, and advancement side effects.
Once a fallback portal has been assigned, later Overworld Eye of Ender throws use
it only while vanilla or forced stronghold progression is still unavailable, so
a saved emergency portal does not mask a later-valid stronghold path.
The frame block writes remain canonical, and the player completes the portal by
filling the remaining eyes through vanilla `EnderEyeItem.useOn(...)` behavior.
Repair passes preserve eyes inserted by players and do not remove an already
formed End portal interior.

`/globeworld end_portal` reports the active policy, stronghold ring candidate
counts, wrapped alias counts, saved fallback frame position, saved eye mask, and
last validation summary. `/globeworld end_portal validate` also checks
canonical candidate starts and warns that validation may load or generate
`STRUCTURE_STARTS` chunks.

## Key Files

- `mod-fabric/src/main/java/globe/world/util/TerrainMode.java`
- `mod-fabric/src/main/java/globe/world/util/PeriodicNoiseUtil.java`
- `mod-fabric/src/main/java/globe/world/util/PeriodicPositionalRandomFactory.java`
- `mod-fabric/src/main/java/globe/world/config/GlobeSettings.java`
- `mod-fabric/src/main/java/globe/world/config/GlobeSettingsHolder.java`
- `mod-fabric/src/main/java/globe/world/config/TopologySettings.java`
- `mod-fabric/src/main/java/globe/world/config/PresentationSettings.java`
- `mod-fabric/src/main/java/globe/world/config/GameplaySettings.java`
- `mod-fabric/src/main/java/globe/world/config/GlobeConfig.java`
- `mod-fabric/src/client/java/globe/world/client/GlobeSeedPreflight.java`
- `mod-fabric/src/client/java/globe/world/client/GlobeWorldSettingsControls.java`
- `mod-fabric/src/main/java/globe/world/util/GenerationWindow.java`
- `mod-fabric/src/main/java/globe/world/util/WorldGenSpillover.java`
- `mod-fabric/src/main/java/globe/world/util/StructurePlacementShifts.java`
- `mod-fabric/src/main/java/globe/world/util/ForcedProgressionStructures.java`
- `mod-fabric/src/main/java/globe/world/util/ForcedProgressionStructurePieces.java`
- `mod-fabric/src/main/java/globe/world/util/ForcedFortressProgressionChestPiece.java`
- `mod-fabric/src/main/java/globe/world/util/ForcedFortressWartPatchPiece.java`
- `mod-fabric/src/main/java/globe/world/util/EndPortalAvailability.java`
- `mod-fabric/src/main/java/globe/world/util/EndPortalProgressionState.java`
- `mod-fabric/src/main/java/globe/world/util/EndPortalFallback.java`
- `mod-fabric/src/main/java/globe/world/mixin/DensityFunctionsNoiseMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/DensityFunctionsShiftMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/DensityFunctionsShiftAMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/DensityFunctionsShiftBMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/DensityFunctionsShiftedNoiseMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/DensityFunctionsWeirdScaledSamplerMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/BlendedNoiseMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/SurfaceSystemMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/NoiseBasedChunkGeneratorMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/WorldGenRegionMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/LevelChunkPostProcessMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/RandomSpreadStructurePlacementMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/StructureGenerationContextMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/StructurePlacementMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/StructureStartMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ChunkStatusTasksMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/EnderEyeItemMixin.java`

## Related Vanilla Mechanics

- [Vanilla world generation](../vanilla-mechanics/world-generation.md)
- [Vanilla structure edge generation](../vanilla-mechanics/structure-edge-generation.md)

## Open Audits

- Validate periodicity for Nether terrain/noise and features.
- Audit structure query and persistence paths.
- Add a controlled virtual feature-origin pass for edge features such as monster
  rooms, or allow selected alias feature decoration to spill wrapped writes.
- Verify tiny-tile terrain is presented as stylized rather than vanilla-identical.
