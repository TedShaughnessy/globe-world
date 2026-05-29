# Plans

This folder holds proposed or investigative work. When a plan is implemented,
move its durable mechanics into [../mod-mechanics/README.md](../mod-mechanics/README.md)
and keep only historical investigation here if it is still useful.

## Completed Plans

1. [Seamless Wrapping Plan](seamless-wrapping-plan.md): implemented terrain
   generation direction.
2. [Terrain Periodicity Investigation](terrain-periodicity-investigation.md):
   completed investigation of compact torus, edge blend, and periodic lattice
   terrain modes.
3. [Chunk Alias Tracker Lifecycle Plan](chunk-alias-tracker-lifecycle-plan.md):
   implemented dimension-scoped alias tracking and lifecycle cleanup.

## Recommended Implementation Order

1. [Worldgen Spillover Lifecycle Plan](worldgen-spillover-lifecycle-plan.md):
   active lifecycle work; clear and diagnose pending wrapped worldgen writes and
   retained level references.
2. [Light Update Alias Fanout Plan](light-update-alias-fanout-plan.md): active
   client-world correctness work; relabel incremental light updates for every
   loaded alias after alias tracking is dimension-safe.
3. [Packet Virtualization Audit Plan](packet-virtualization-audit-plan.md):
   active audit work; inventory and close remaining position-bearing packet
   leaks after the known light-update gap is addressed.
4. [Entity Canonical Storage Plan](entity-canonical-storage-plan.md): active
   entity correctness work; keep moving non-player entities stored in canonical
   X/Z after the packet and lifecycle foundations are tighter.

## Optional Optimizations

1. [Client Canonical Chunk Cache](client-canonical-chunk-cache.md): optional
   client-side alias warm-start cache proposal; defer until the server packet
   and lifecycle behavior is stable.
