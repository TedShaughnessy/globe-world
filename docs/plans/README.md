# Plans

This folder holds proposed or investigative work. When a plan is implemented,
move its durable mechanics into [../mod-mechanics/README.md](../mod-mechanics/README.md)
and keep only historical investigation here if it is still useful.

## Active Plans

1. [Seamless Wrapping Plan](seamless-wrapping-plan.md): true periodic terrain
   generation direction.
2. [Terrain Periodicity Investigation](terrain-periodicity-investigation.md):
   analysis of compact torus, edge blend, and periodic lattice terrain modes.
3. [Client Canonical Chunk Cache](client-canonical-chunk-cache.md): client-side
   alias warm-start cache proposal.
4. [Entity Canonical Storage Plan](entity-canonical-storage-plan.md): keep
   moving non-player entities stored in canonical X/Z.
5. [Light Update Alias Fanout Plan](light-update-alias-fanout-plan.md): relabel
   incremental light updates for every loaded alias.
6. [Chunk Alias Tracker Lifecycle Plan](chunk-alias-tracker-lifecycle-plan.md):
   scope alias tracking by dimension and clear stale records.
7. [Packet Virtualization Audit Plan](packet-virtualization-audit-plan.md):
   inventory and close remaining position-bearing packet leaks.
8. [Worldgen Spillover Lifecycle Plan](worldgen-spillover-lifecycle-plan.md):
   clear and diagnose pending wrapped worldgen writes.
