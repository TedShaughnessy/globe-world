# Low-Risk Implementation Plan

## Status

The first low-risk v2 implementation pass is complete and tested manually. The
durable behavior has been folded into the mod mechanics docs:

- `TopologyContext` / `TopologyContexts`: implemented. See
  [Topology](../../mod-mechanics/topology.md).
- Packet policy table: implemented as a code registry plus mechanics docs. See
  [Packet Policies](../../mod-mechanics/packet-policies.md).
- Diagnostics channels: implemented as session-only command-gated channels. See
  [Client](../../mod-mechanics/client.md#local-sky-and-diagnostics).
- `ActorLocalTargetView` / `ActorLocalTargets`: implemented as a facade over
  existing AI alias helpers. See [Entities](../../mod-mechanics/entities.md).
- Clean settings split: partially implemented as a compatibility layer. See
  [Topology](../../mod-mechanics/topology.md#implemented-paths) and
  [Client](../../mod-mechanics/client.md#packet-and-cache-model).

This plan now tracks only follow-up work that is not fully implemented.

## Completed Milestones

### 1. Topology Context Facade

Completed:

- Added `TopologyContext` and `TopologyContexts`.
- Kept `CoordUtil` as the arithmetic source of truth.
- Migrated low-risk call sites in `GlobeDebugCommands`,
  `WorldEventPacketUtil`, and `AiAliasUtil`.
- Documented `TopologyContext` as the named coordinate-frame boundary.

### 2. Packet Policy Table

Completed:

- Added `PacketPolicyCategory`, `PacketVirtualizationPolicy`, and
  `PacketVirtualizationPolicies`.
- Added `docs/mod-mechanics/packet-policies.md`.
- Kept existing handwritten `instanceof` packet dispatch and packet copy logic.

### 3. Diagnostics Channels

Completed:

- Added `DiagnosticsChannel` and `GlobeDiagnostics`.
- Added `/globeworld debug list`, `enable`, `disable`, and `clear`.
- Moved investigation logs in chunk loading, client action rejection, block
  mutation, worldgen spillover, client chunk cache, portal diagnostics, packet
  fanout, alias cleanup, and tracking-view diagnostics behind channels.
- Preserved existing `GW_...` message identifiers inside gated messages.

### 4. Actor-Local Target View

Completed:

- Added `ActorLocalTargetView` and `ActorLocalTargets`.
- Migrated `/globeworld entity`, `ServerEntityGetterMixin`, and
  `LookAtPlayerGoalMixin` to consume the new facade.
- Kept `AiAliasUtil` as the source of behavior while callers migrate.

### 5. Settings Split Compatibility Layer

Partially completed:

- Added `TopologySettings`, `PresentationSettings`, `GameplaySettings`,
  `DiagnosticsSettings`, and `GlobeSettings`.
- `GlobeConfig` now maintains a split `GlobeSettings` view alongside the
  current saved/wire `TilingSettings` shape.
- `DimensionTiling` reads topology through `TopologySettings`.
- Runtime commands still mutate only presentation/gameplay fields.

The full schema migration is intentionally not complete in this pass.

## Remaining Low-Risk Follow-Up

### Finish Clean Settings Split

Goal: make `GlobeSettings` the saved and synchronized settings model rather
than only a compatibility view.

Remaining targets:

- Worldgen/server saved settings codec.
- Fabric configuration/play networking payloads.
- Client settings receiver and `GlobeClientTilingSettings`.
- World creation state and settings UI construction.
- `/globeworld config` reporting, if it should show split groups directly.
- Clear rejection or importer plan for old saved settings if the on-disk schema
  changes.

Verification:

- Build.
- Create a new world with Overworld tiling enabled and Nether tiling disabled.
- Join an integrated server and verify settings sync.
- Change curvature/day length at runtime and verify client updates.
- Confirm topology fields are not exposed as normal runtime mutations.

### Continue Caller Migration

These facades are now available, but many older callers still correctly use
`CoordUtil`, `AiAliasUtil`, and direct `TilingSettings` access. Future cleanup
can migrate them gradually when touching nearby behavior:

- packet helpers can use `TopologyContext` names at boundaries;
- AI/range/pathing mixins can consume `ActorLocalTargets`;
- config readers can move through `GlobeConfig` split accessors.

## Out Of Scope

These remain future/high-risk work and should keep their own plans:

- general topological block clipping;
- projectile swept movement;
- broad entity query replacement;
- `GenerationWindow` / full toroidal worldgen window.

## Cross-Milestone Checks

Keep these checks green after future migration work:

- canonical tile remains the only durable mutable state;
- client chunk cache remains vanilla-shaped;
- aliases remain unsaved presentation/query frames;
- Overworld and Nether use their own topology settings;
- End remains vanilla/untiled;
- runtime topology mutation is not exposed as a normal command/UI flow;
- diagnostics are quiet by default;
- docs in `docs/mod-mechanics/` are updated when APIs become durable behavior.
