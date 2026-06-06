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

`TerrainMode` selects a generation strategy from tile size:

- `COMPACT_TORUS`: tiny/stylized tiles.
- `EDGE_BLEND`: arbitrary medium/large sizes where preserving vanilla scale in
  the interior matters.
- `PERIODIC_LATTICE`: clean sizes where true periodic lattice noise can close
  the tile.

The current policy uses compact torus for small tiles, periodic lattice for
clean large multiples, and edge blend for awkward medium/large sizes.

## Implementation

Terrain and biome hooks route many X/Z-dependent samples through periodic noise
utilities or terrain-mode-aware sampling. Positional random factories are wrapped
where the caller's coordinate unit is known. Generator phases that rely on raw
ambient `CoordUtil` calls run inside scoped dimension tiling contexts; async
biome/noise work captures the caller's dimension context and restores it on the
worker thread.

Alias `LevelChunk.postProcessGeneration` is cancelled and queued
post-processing offsets are cleared so alias neighbor-shape fixes do not write
through wrapped `Level.setBlock` into canonical storage.

Worldgen spillover handles block writes that wrap across X/Z tile edges during
generation. `WorldGenRegion` write hooks enqueue the wrapped canonical block
state into server-owned transient spillover state keyed by dimension and
canonical chunk. Each queued write also records the block state observed when
vanilla accepted the original write. Canonical chunks apply queued spillover
during biome decoration, post-processing, and before chunk packet serialization,
but replay a queued write only if the destination still matches the observed
state. This prevents stale leaf, grass, or other decoration writes from
overwriting trunks and other blocks placed by the destination chunk after the
spillover write was queued. Level close and server stop discard any remaining
queues and warn if writes were abandoned; spillover queues are runtime
bookkeeping and are not saved world data.

Structure edge handling stores virtual source keys during reference generation,
resolves them during biome decoration, and places vanilla starts with a
whole-tile chunk-box shift. Alias starts are treated as transient worldgen data.
Alias biome decoration and reference generation are skipped without leaking
their tiling context into surrounding generation work.

End portal progression uses canonical stronghold ownership instead of alias
structure lookup. In the Overworld, `EndPortalAvailability` inspects vanilla
stronghold concentric-ring positions and treats the world as having a durable
stronghold only when a raw ring candidate is already inside the canonical tile.
Wrapped alias candidates are reported for diagnostics but do not count as owned
world state. When tiling is enabled and no canonical stronghold candidate exists
or structure generation is disabled, `EnderEyeItemMixin` replaces a thrown Eye of
Ender with a fallback path: `EndPortalFallback` chooses and persists one
canonical position centered on the throwing player, repairs a 5x5 End portal
frame with a deterministic random subset of eyes already inserted, then spawns a
normal Eye of Ender. The eye entity and its flight target stay in canonical
server coordinates so entity storage canonicalization cannot desynchronize the
projectile from its target; entity packets still render the eye through the
nearest visual alias for the throwing player. The fallback path preserves
vanilla item use, stat, sound, and advancement side effects.
Once a fallback portal has been assigned, later Overworld Eye of Ender throws use
that saved portal target before falling back to vanilla stronghold lookup, so
players keep receiving directions to the portal they can actually complete.
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
- `mod-fabric/src/main/java/globe/world/util/WorldGenSpillover.java`
- `mod-fabric/src/main/java/globe/world/util/StructurePlacementShifts.java`
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
