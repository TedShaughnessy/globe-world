# V2 Architecture Plan

## Goal

V2 keeps Globe World's core model: one canonical mutable world tile with
viewer-facing virtual aliases. The rebuild goal is not a different feature; it
is better encapsulation around the concepts v1 discovered through many targeted
mixins.

V1 can finish first. This plan exists so later work can consolidate behavior
instead of growing a second generation of isolated fixes.

## Design Principles

- Canonical state remains the only durable state.
- Virtual aliases remain presentation and query frames, not saved owners.
- The client chunk cache remains vanilla-shaped.
- Coordinate-frame conversions should be explicit and named.
- Common topology operations should live behind shared primitives, not repeated
  per subsystem.
- Debugging should be opt-in and structured enough to support playtesting.

## Sections

1. [Coordinate Frames](coordinate-frames.md): name the position frames that v1
   currently represents with ordinary `BlockPos`, `ChunkPos`, and `Vec3`.
2. [Topology Access Layer](topology-access-layer.md): centralize canonical
   state access and actor-relative alias queries.
3. [Packet Virtualization](packet-virtualization.md): keep packet relabeling,
   but make packet position policies easier to audit.
4. [Entities And AI](entities-and-ai.md): use local alias target objects instead
   of per-goal coordinate patches wherever possible.
5. [Topological Raycasts](topological-raycasts.md): make wrapped raycast and
   collision behavior a first-class primitive for picking, line of sight, and
   projectiles.
6. [Worldgen Window](worldgen-window.md): replace ad hoc spillover handling with
   an explicit toroidal generation view where possible.
7. [Configuration And Diagnostics](configuration-and-diagnostics.md): separate
   fixed topology, runtime presentation, and debug controls.
8. [Migration Strategy](migration-strategy.md): build v2 in layers without
   risking the completed v1 feature set.
9. [Viability Audit](viability-audit.md): compare the v2 direction with the
   feature-complete v1 code and call out extraction opportunities, risks, and
   concrete implementation anchors.
10. [Low-Risk Implementation Plan](low-risk-implementation-plan.md): concrete
    status for the implemented low-risk primitives and the remaining settings
    split follow-up.

## Implemented Low-Risk Primitives

The first low-risk v2 pass has been implemented and folded into
[Globe World Mod Mechanics](../../mod-mechanics/README.md):

- `TopologyContext` and `TopologyContexts`: see
  [Topology](../../mod-mechanics/topology.md).
- Packet policy registry/table: see
  [Packet Policies](../../mod-mechanics/packet-policies.md).
- Diagnostics channels and `/globeworld debug`: see
  [Client diagnostics](../../mod-mechanics/client.md#local-sky-and-diagnostics).
- `ActorLocalTargetView` and `ActorLocalTargets`: see
  [Entities](../../mod-mechanics/entities.md).
- Split settings records as a compatibility layer: see
  [Topology](../../mod-mechanics/topology.md) and
  [Client](../../mod-mechanics/client.md#packet-and-cache-model).

Remaining architecture work should build from those mechanics docs rather than
the original low-risk checklist.

## Non-Goals

- Do not replace continuous wrapping with border teleportation.
- Do not make client-side canonical chunk storage the default design.
- Do not require v1 to be rewritten before feature completion.
- Do not hide vanilla identity. Blocks, chunks, entities, and structure starts
  still need clear canonical ownership.

## Completion Shape

V2 is ready to begin when v1's feature set is stable enough that remaining work
is mostly polish, mod-interop work, or bug fixing. The first v2 milestone
should be an internal topology API and tests, not visible gameplay changes.
