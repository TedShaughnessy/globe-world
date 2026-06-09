# Topology Access Layer

## Problem

V1 has strong central helpers, but many subsystems still decide locally when to
canonicalize. Chunk lookup, block mutation, block entities, ticks, spawning,
tracking, and player actions all have their own hooks.

This is understandable for v1, but it means the topology invariant is enforced
by many small agreements with vanilla call sites.

## V2 Direction

Build a topology access layer that owns common world questions:

- What canonical chunk owns this raw chunk?
- Is this block mutation allowed from an alias frame?
- Which aliases of this canonical chunk are visible to this player?
- Which canonical chunk should tick because an alias is in range?
- What actor-local frame should an entity query use?

The layer should expose named operations such as:

```text
canonicalChunk(rawChunk, dimension)
canonicalBlock(rawBlock, dimension)
visibleChunkFor(player, canonicalChunk)
loadedAliasesFor(player, canonicalChunk)
shouldAllowAliasMutation(player, rawBlock)
actorLocalTarget(actor, target)
```

## Requirements

- The access layer must be dimension-aware and support disabled dimensions.
- It must not create durable alias state.
- It should be callable from mixins, utilities, commands, and diagnostics.
- It should support server-only authority and client-only presentation helpers
  without mixing the two.

## Implementation Sketch

Start with a `TopologyContext` resolved from `ServerLevel`, `ClientLevel`,
`ResourceKey<Level>`, or scoped worldgen context. Keep low-level math separate
from policy:

- Math: wrapping, virtual offsets, shortest deltas.
- Policy: mutation gates, simulation caps, alias visibility, packet fanout.

V2 mixins should become adapters from vanilla entry points into this layer.

## Expected Benefits

- Less duplicated wrapping logic.
- A single place to reason about alias mutation and ticking rules.
- Cleaner testing of topology policy without needing every vanilla subsystem.
- Better naming for commands and debug reports.

## Open Questions

- Which current utilities become part of the layer, and which stay separate?
- Should alias visibility tracking live inside the layer or remain a dedicated
  chunk/packet service?
- How much of `Level`/`ServerChunkCache` access can be hidden behind the layer
  without making mixins harder to read?
