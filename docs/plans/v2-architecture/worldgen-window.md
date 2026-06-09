# Worldgen Window

## Problem

V1 combines periodic terrain sampling, scoped dimension context, alias
generation skips, structure shifts, and spillover queues. This works, but
generation writes near tile edges are subtle because vanilla phases were not
designed for toroidal ownership.

Spillover queues with observed-state guards are a pragmatic v1 fix. A rebuild
can make the toroidal generation view more explicit.

## V2 Direction

Model worldgen as operating inside a toroidal generation window. The generator
should know when a read or write is:

- inside the canonical chunk,
- inside a wrapped neighbor,
- deferred to another canonical chunk,
- unsafe because the destination cannot be observed yet.

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
