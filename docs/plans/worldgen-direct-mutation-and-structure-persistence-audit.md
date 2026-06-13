# Worldgen Direct Mutation And Structure Persistence Audit Plan

This plan resolves the medium-risk direct chunk mutation, structure
query/persistence, and related worldgen edges raised in the
[Minecraft coordinate coverage audit](minecraft-coordinate-coverage-audit.md).

## Problem

Runtime gameplay mostly flows through canonicalized chunk/block APIs, and
worldgen has substantial coverage through terrain hooks, `GenerationWindow`,
`WorldGenRegionMixin`, direct section access hooks, and spillover replay.

The remaining risks are edge cases where vanilla or another generator mutates
chunk/section state directly, or where structure query/persistence treats alias
starts as real durable data.

## Goals

- Audit direct chunk and section mutation paths against current hooks.
- Decide whether unobserved spillover should reconstruct selected side effects.
- Audit structure lookup, locate, references, and saved starts for alias leaks.
- Keep canonical chunks as the only durable owners.
- Record explicit out-of-scope behavior for rare generator paths.

## Non-Goals

- Rewriting terrain generation.
- Persisting alias chunks, alias starts, or alias structure references.
- Guaranteeing every third-party worldgen provider is fully toroidal.

## Vanilla Source Anchors

- `net/minecraft/server/level/WorldGenRegion.java`
- `net/minecraft/world/level/chunk/ChunkAccess.java`
- `net/minecraft/world/level/chunk/LevelChunkSection.java`
- `net/minecraft/world/level/chunk/BulkSectionAccess.java`
- `net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java`
- carvers, features, ore placement, surface system, and retrogen sources
- structure placement, reference, start, locate, and persistence classes

## Current Globe Anchors

- `GenerationWindow`
- `WorldGenSpillover`
- `WorldGenRegionMixin`
- `BulkSectionAccessMixin`
- `LevelChunkPostProcessMixin`
- `StructurePlacementShifts`
- `RandomSpreadStructurePlacementMixin`
- `StructureGenerationContextMixin`
- `StructurePlacementMixin`
- `StructureStartMixin`
- `ForcedProgressionStructures`
- `worldgen.md`

## Direct Mutation Audit

Build a source-backed list of direct mutation paths that can bypass
`WorldGenRegion.setBlock(...)`:

- chunk/section `setBlockState` calls;
- direct block entity attachment;
- direct scheduled block/fluid tick creation;
- POI side effects;
- heightmap/light/post-processing marks;
- retrogen and spawn-platform paths.

For each path, classify it as:

- covered by current hooks;
- covered only when the destination chunk is visible in the active generation
  window;
- unobserved spillover limitation;
- intentionally out of scope;
- requires a new hook.

## Concrete Implementation Plan

### 1. Create The Direct Mutation Matrix

Add `docs/plans/worldgen-direct-mutation-matrix.md`.

Matrix columns:

| Source | Phase | Mutation | Current Globe coverage | Risk | Action |
| --- | --- | --- | --- | --- | --- |

Required source groups:

- `WorldGenRegion.setBlock(...)`
- `ChunkAccess.setBlockState(...)`
- `LevelChunkSection.setBlockState(...)`
- `BulkSectionAccess`
- `NoiseBasedChunkGenerator`
- carvers;
- ore and feature placement;
- surface system;
- below-zero retrogen;
- flat generator spawn platform;
- chunk post-processing;
- block entity creation during generation;
- POI refresh during generation;
- scheduled block/fluid tick creation during generation.

Coverage statuses:

- `Covered by WorldGenRegionMixin`
- `Covered by GenerationWindow`
- `Covered by WorldGenSpillover observed replay`
- `Covered by BulkSectionAccessMixin`
- `Canonical chunk only`
- `Unobserved spillover limitation`
- `Out of scope`
- `Needs hook`

Risk mitigation:

- Do not add a hook until the matrix row identifies the source phase and the
  state owner that could be bypassed.
- Keep matrix entries source-anchored with jar-internal paths and method names.
- Prefer documenting an explicit boundary over adding a broad chunk-access hook.

### 2. Verify Existing Worldgen Helpers Before New Hooks

For each `Needs hook` candidate, check whether the fix belongs in an existing
boundary:

- `GenerationWindow` for bounded canonical read/write classification;
- `WorldGenRegionMixin` for worldgen region block writes and marks;
- `WorldGenSpillover` for deferred cross-edge writes;
- `BulkSectionAccessMixin` for direct section lookup/write paths;
- structure mixins for shifted starts/references.

Only create a new helper if the source path does not fit those boundaries.

Potential new helpers:

- `WorldGenSideEffectSpillover` only if a reproducible vanilla case needs
  replay of block entity, POI, or scheduled tick side effects.
- `StructurePersistenceAudit` utility only if repeated manual checks need a
  command/report to compare saved starts and references.

### 3. Keep Unobserved Spillover State-Only By Default

Default decision:

- keep unobserved spillover replay limited to block state;
- document block entities, POIs, scheduled ticks, and fluid ticks as
  limitations unless a vanilla reproduction proves they matter.

Escalation criteria for richer spillover payloads:

- the issue reproduces in vanilla generation, not only a third-party provider;
- the missed side effect is durable or user-visible after chunk save/reload;
- the side effect can be reconstructed without loading unrelated chunks;
- replay can be guarded against stale destination state.

If criteria pass, implement a narrow payload:

- `BlockEntityIntent(canonicalPos, blockEntityNbtOrType, observedState)`
- `ScheduledTickIntent(canonicalPos, tickType, delay, priority, observedState)`
- `PoiIntent(canonicalPos, poiType, observedState)`

Risk mitigation:

- Never replay side effects unguarded if the destination state was observed.
- Never save spillover queues as world data.
- Drop and warn on level close/server stop like current `WorldGenSpillover`.

### 4. Structure Persistence Audit

Add a source-backed structure section to the matrix covering:

- `StructureStart` save/load keys;
- reference placement and shifted reference storage;
- structure locate/query code paths;
- forced stronghold and fortress starts;
- fallback End portal state.

Manual check procedure:

1. Generate a small tiled Overworld and Nether.
2. Force or locate stronghold/fortress generation near a seam.
3. Save and stop the world.
4. Reload.
5. Run existing `/globeworld end_portal validate` and any fortress diagnostic
   available.
6. Inspect saved starts/references for canonical chunk keys only.

Action rules:

- If alias starts persist, fix the structure mixin that writes starts or
  references.
- If only `locate` reports raw/alias-surprising output, leave it to the raw
  vanilla command policy in
  [Commands And Admin Coordinates](../mod-mechanics/commands.md).
- If forced progression starts fail after reload, fix
  `ForcedProgressionStructures` or its reference generation path.

### 5. Optional Diagnostics

Add diagnostics only if matrix/manual checks are too opaque.

Candidate command:

```text
/globeworld worldgen_audit <chunk>
```

Output:

- canonical chunk;
- raw requested chunk;
- queued spillover writes for that canonical chunk;
- structure starts at canonical key;
- structure references for canonical key;
- warning if alias starts/references are present.

Risk mitigation:

- Keep the command read-only.
- Require gamemaster permission if it may load or generate chunks.

## Unobserved Spillover Side Effects

Current unobserved spillover replays block state only. It does not fully
reconstruct attached block-entity NBT, scheduled block/fluid ticks, or POI side
effects from providers that did not expose the destination chunk in the active
`WorldGenRegion` cache.

Decision options:

1. Keep current behavior and document it as an external-provider limitation.
2. Reconstruct a small set of side effects for known vanilla blocks where safe.
3. Add a richer spillover payload that records block entity/tick/POI intents.

Recommended first choice: Option 1 plus targeted tests. Move to Option 2 only
if vanilla generation produces a reproducible seam bug.

## Structure Query And Persistence Audit

Audit these paths:

- saved starts and references for alias coordinates;
- `locate` and structure query behavior near seams;
- forced stronghold and fortress starts after save/reload;
- edge-crossing pieces that rely on shifted references;
- third-party or external generation of structure chunks.

Desired policy:

- canonical starts and canonical references are durable;
- alias starts remain transient placement/query helpers;
- `locate` may remain raw vanilla unless command policy changes;
- progression mechanics use canonical ownership and existing forced/fallback
  systems.

## Implementation Phases

### Phase 1: Direct Mutation Matrix

Create a table in this plan or a new vanilla mechanics page listing direct
mutation sources and current coverage.

Acceptance criteria:

- Every known vanilla direct mutation path has a status.
- Unobserved spillover limitations are precise rather than broad.

### Phase 2: Regression Worlds

Create manual generation checks for:

- trees/features crossing each edge;
- ores or carvers crossing each edge;
- blocks with block entities during generation, if any vanilla examples exist;
- scheduled-fluid or scheduled-block side effects, if any reproducible vanilla
  examples exist;
- retrogen or spawn-platform special cases.

### Phase 3: Structure Persistence Checks

Generate, save, reload, and query structures near edges:

- forced stronghold;
- forced Nether fortress;
- ordinary edge-crossing structure;
- small-tile fallback End portal policy.

Acceptance criteria:

- No alias starts become durable owners after reload.
- Forced progression starts still validate after reload.
- References needed for edge pieces remain available.

### Phase 4: Targeted Fixes Or Explicit Deferrals

Only add hooks for reproducible issues. If no issue is reproducible, update
`worldgen.md` with the explicit audited boundary.

## Documentation Updates When Implemented

- Update `docs/mod-mechanics/worldgen.md` with the final direct-mutation and
  structure-persistence policy.
- Add or update vanilla mechanics references if the direct mutation matrix
  becomes source-heavy.
- Shrink the medium-risk worldgen sections in
  `minecraft-coordinate-coverage-audit.md`.
