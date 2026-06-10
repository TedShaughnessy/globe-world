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
- Runtime block/chunk helper migration: implemented for server chunk lookup,
  block mutation/block-entity access, canonical alias tickets, chunk packet
  relabeling, block packet fanout, biome resend fanout, world-event block
  fanout, tick canonicalization, spawning chunk collection, chunk player
  provider checks, client mutation guards, and bulk section access. See
  [Topology](../../mod-mechanics/topology.md),
  [Chunks](../../mod-mechanics/chunks.md), and
  [Blocks And Ticks](../../mod-mechanics/blocks-and-ticks.md).
- Entity/waypoint packet helper migration: implemented for receiver-nearest
  entity packet aliases, waypoint block/chunk/azimuth aliases, wrapped waypoint
  range checks, and waypoint chunk visibility. See
  [Entities](../../mod-mechanics/entities.md).
- Clean settings split: implemented as the saved/network settings model. See
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

### 5. Runtime Block/Chunk Helper Migration

Completed:

- Added topology-context access helpers for canonical chunk/block ownership,
  canonical chunk lookup from block/section positions, viewer-facing
  block/chunk placement, canonical checks, loaded aliases, and wrapped chunk
  distances.
- Added `shouldAllowAliasMutation` / `AliasMutationAccess` as the server-side
  alias mutation policy boundary used by client action diagnostics.
- Migrated server chunk lookup, block mutation/block-entity access, canonical
  alias tickets, full chunk packet relabeling, block packet fanout, biome
  resend fanout, world-event block fanout, tick canonicalization, spawning
  chunk collection, chunk player provider checks, client mutation guards, and
  bulk section access to those names.
- Migrated entity and waypoint packet helpers to use `TopologyContext` names
  for viewer-nearest aliases, canonicalization, wrapped range checks, and
  waypoint visibility.
- Kept `CoordUtil` as the arithmetic source of truth and `ChunkAliasTracker` as
  the backing loaded-alias store.

### 6. Settings Split Compatibility Layer

Completed:

- Added `TopologySettings`, `PresentationSettings`, `GameplaySettings`,
  and `GlobeSettings`.
- `GlobeSettings` is now the saved and synchronized settings shape. Old saved
  `TilingSettings` data is not imported.
- `DimensionTiling` reads topology through `TopologySettings`.
- Runtime commands still mutate only presentation/gameplay fields.
- The world creation state and settings UI now pass `GlobeSettings`; topology
  is world-creation-only, while presentation and gameplay can be edited at
  runtime.
- The saved-settings holder is named `GlobeSettingsHolder`. Settings UI controls
  now mutate `TopologySettings`, `PresentationSettings`, and `GameplaySettings`
  directly.
- `TilingSettings` has been removed; split settings records own normalization
  and update helpers directly.

Verification:

- Build.
- Create a new world with Overworld tiling enabled and Nether tiling disabled.
- Join an integrated server and verify settings sync.
- Change curvature/day length at runtime and verify client updates.
- Confirm topology fields are not exposed as normal runtime mutations.

### Continue Caller Migration

These facades are now available, but many older callers still correctly use
`CoordUtil` and `AiAliasUtil` as internal adapters. Future cleanup can migrate
them gradually when touching nearby behavior:

- AI/range/pathing mixins can consume `ActorLocalTargets`;
- remaining config readers can move through `GlobeConfig` split accessors and
  direct split settings helpers as nearby code is touched.

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
