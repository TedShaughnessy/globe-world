# Worldgen Improvements

## Goal

World generation should become easier to reason about at tile edges without
changing Globe World's core ownership model. Canonical chunks remain the only
durable generated chunks; aliases remain virtual views used for reads,
structure lookup, packet presentation, and edge-placement context.

This is a standalone feature-improvement plan, not part of the v2 architecture
migration. The implemented behavior is documented in
[Worldgen](../mod-mechanics/worldgen.md); this page tracks a possible rebuild
of worldgen edge handling.

## Problem

The current implementation combines periodic terrain sampling, scoped dimension
context, alias generation skips, structure shifts, and spillover queues. This
works, but generation writes near tile edges are subtle because vanilla phases
were not designed for toroidal ownership.

Spillover queues with observed-state guards are a pragmatic fix. A future
improvement can make the toroidal generation view more explicit.

## Direction

Model worldgen as operating inside a toroidal generation window. The generator
should know when a read or write is:

- inside the canonical chunk;
- inside a wrapped neighbor;
- deferred to another canonical chunk;
- unsafe because the destination cannot be observed yet.

## Current Anchors

The existing implementation already behaves like an implicit generation window:

- Worldgen chunk reads map requested chunks into the physical `WorldGenRegion`
  cache when the wrapped alias is present.
- Block, fluid, and block-entity reads canonicalize positions.
- `ensureCanWrite` allows wrapped writes when the canonical target is within the
  current generation step's write radius.
- `setBlock` writes canonical positions, enqueues deferred spillover when the
  physical cache cannot observe the target, and records expected state when it
  can.
- `WorldGenSpillover` stores deferred writes by dimension and canonical chunk,
  applies them only to canonical chunks, and skips writes when the observed
  state has diverged.
- `ChunkGeneratorMixin` scopes generation with `DimensionTiling.runWith`, skips
  non-canonical decoration, and applies spillover around decoration.
- Structure references and shifted placement are handled by
  `ChunkGeneratorMixin` and `StructureStartMixin`.

## Requirements

- Terrain, biome, surface, cave, feature, and structure decisions must share the
  same dimension topology.
- Alias chunks must not become durable structure or feature owners.
- Wrapped writes should be replayed deterministically and only once.
- Missing destination chunks must be handled explicitly rather than hidden as
  ordinary vanilla absence.
- External generation providers should have a clear degraded path.

## Implementation Sketch

Introduce a `GenerationWindow` around `WorldGenRegion`-like access:

```text
readBlock(rawOrGenerationPos)
writeBlock(generationPos, state, writePolicy)
markPostProcess(generationPos)
structureSourceFor(targetChunk)
flushDeferredWrites(canonicalChunk)
```

The window can own the deferred-write queue and the observed-state policy. This
keeps spillover behavior attached to generation semantics instead of ordinary
runtime block mutation.

The wrapper must preserve generation-phase semantics. `WorldGenRegion`
`ensureCanWrite`, `ChunkStep.blockStateWriteRadius`, structure reference
creation, and structure placement shifts are not generic runtime block
mutation. A generation window should live in worldgen code and own:

- canonical read/write mapping;
- physical cache availability checks;
- deferred spillover ownership;
- expected-state guards;
- structure-source shifts and canonical-only persistence.

## Expected Benefits

- Cleaner structure and feature edge behavior.
- Fewer special cases in individual generation mixins.
- Better diagnostics for abandoned or stale spillover writes.
- Easier reasoning about tiny tiles and external worldgen consumers.

## Open Questions

- Can vanilla generation phases be wrapped deeply enough without worse mixin
  fragility?
- Which feature origins should be allowed to virtually decorate from alias
  positions?
- How much structure query/persistence behavior should be canonical-only versus
  virtual-query-aware?
- Which external worldgen providers bypass `WorldGenRegion` or mutate chunks in
  unusual phases, and what compatibility adapter or degraded path should they
  get?
