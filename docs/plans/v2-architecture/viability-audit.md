# V2 Viability Audit

## Context

As of the feature-complete v1 code, the v2 plan is viable as an incremental
extraction and reorganization. The core mechanics already exist: canonical
storage, viewer-facing aliases, dimension-specific topology, loaded-alias
packet fanout, canonical entity storage, actor-local AI helpers, and toroidal
worldgen edge handling.

The main v2 risk is not proving the model. The risk is moving code out from
many precise vanilla hooks without losing the hard-won details attached to
those hooks.

V2 does not need backward compatibility with v1 worlds or saved settings. That
removes a large source of schema and migration pressure: v2 can use clean saved
settings, reject old worlds clearly, and avoid compatibility shims unless they
are later added as an explicit import/migration tool.

## Low-Risk, High-Value Slices

Several v2 pieces are low risk because they can be added as naming,
organization, or diagnostics layers around proven v1 behavior before any
gameplay hook changes:

- `TopologyContext` facade: wrap `DimensionTiling` and `CoordUtil` behind
  explicit frame names while returning vanilla `BlockPos`, `ChunkPos`, `AABB`,
  and `Vec3` types. This makes code review better without changing runtime
  behavior.
- Packet policy table: document and organize existing policies from
  `BlockPacketUtil`, `ChunkPacketUtil`, `WorldEventPacketUtil`, and
  `EntityPacketUtil`. The first version can be docs plus code grouping, with no
  packet semantics changed.
- Diagnostics channels: move always-on investigation logs behind named debug
  channels. This is high value for playtesting noise and support quality, and
  can be implemented independently of topology behavior.
- Clean settings split: because backward compatibility is not required,
  topology, presentation, and gameplay can become separate records/codecs
  without carrying old-field compatibility inside the new model. Diagnostics
  should remain session-only command state.
- `ActorLocalTargetView` record: package existing `AiAliasUtil` results into
  one object, then migrate one or two AI call sites as proof. The current helper
  behavior can remain the source of truth.

Status: the first implementation pass for these low-risk slices is complete.
Durable behavior has moved to the mod mechanics docs for
[`TopologyContext`](../../mod-mechanics/topology.md),
[packet policies](../../mod-mechanics/packet-policies.md),
[diagnostics](../../mod-mechanics/client.md#local-sky-and-diagnostics), and
[`ActorLocalTargetView`](../../mod-mechanics/entities.md). The settings split
uses `GlobeSettings` as the actual saved/network schema.

The less-low-risk slices are the ones that change vanilla execution semantics:
general topological block clipping, projectile swept movement, broader entity
query replacement, and the worldgen `GenerationWindow`.

See [Low-Risk Implementation Plan](low-risk-implementation-plan.md) for the
completed low-risk status and remaining follow-up.

## Coordinate Frames

Viability: high.

The proposed frame model mostly names semantics that already exist in
`CoordUtil`:

- `wrapChunk`, `wrapBlock`, `wrapBlockPos`, and `wrapChunkPos` convert raw
  positions to canonical owners (`CoordUtil.java:14`, `CoordUtil.java:35`,
  `CoordUtil.java:140`, `CoordUtil.java:161`).
- `tileAliasChunk`, `tileAliasBlock`, and `isInCanonicalTile` expose alias
  identity and canonical membership (`CoordUtil.java:182`, `CoordUtil.java:197`,
  `CoordUtil.java:212`).
- `wrappedDeltaBlock`, `wrappedDistanceSqr`, and `wrappedChunkDistance` already
  provide topological distance math while preserving ordinary Y deltas
  (`CoordUtil.java:225`, `CoordUtil.java:256`, `CoordUtil.java:368`).
- `virtualBlock`, `virtualAabb`, and `virtualChunk` map canonical owners into a
  viewer/actor-local alias frame (`CoordUtil.java:285`, `CoordUtil.java:316`,
  `CoordUtil.java:350`).

`DimensionTiling` is already close to the proposed `TopologyContext`. It
resolves Overworld, Nether, and disabled End behavior from saved settings
(`DimensionTiling.java:28`) and provides scoped worldgen context through
`with`/`runWith` (`DimensionTiling.java:77`, `DimensionTiling.java:91`).

The main challenge is API shape. Full wrapper records for every temporary
`BlockPos`, `ChunkPos`, and `Vec3` would be noisy inside mixins. A more practical
first step is a `TopologyContext` facade with method names that return existing
vanilla types, plus careful naming at subsystem boundaries:

- `canonicalBlock(raw)`
- `canonicalChunk(raw)`
- `virtualBlockForViewer(canonical, viewer)`
- `actorLocalTarget(actor, target)`
- `wireBlockForReceiver(canonical, receiver)`

This gives v2 reviewability without forcing every vanilla call site to allocate
or unwrap records.

## Topology Access Layer

Viability: high for extraction, moderate for policy consolidation.

There is already a clear split between math and policy, but the policy is spread
across utilities:

- Server chunk lookup canonicalizes in `ServerChunkCacheMixin.getChunk` and
  `getChunkNow` (`ServerChunkCacheMixin.java:50`, `ServerChunkCacheMixin.java:60`).
- Block mutation and block-entity access canonicalize in
  `LevelSetBlockBroadcastMixin` (`LevelSetBlockBroadcastMixin.java:39`,
  `LevelSetBlockBroadcastMixin.java:49`,
  `LevelSetBlockBroadcastMixin.java:54`,
  `LevelSetBlockBroadcastMixin.java:59`).
- Alias mutation rejection is a policy helper in `ClientActionDiagnostics`:
  non-canonical client block actions are rejected when the canonical chunk is
  not block-ticking (`ClientActionDiagnostics.java:19`).
- Scheduled tick storage and lookup canonicalize through `LevelTicksMixin`
  (`LevelTicksMixin.java:23`, `LevelTicksMixin.java:40`,
  `LevelTicksMixin.java:50`).
- Alias visibility is tracked per player and dimension in `ChunkAliasTracker`
  (`ChunkAliasTracker.java:16`, `ChunkAliasTracker.java:53`).

A v2 topology layer should start by wrapping these existing helpers rather than
moving all mixin logic at once. The durable boundary looks like:

- math from `CoordUtil`;
- dimension context from `DimensionTiling`;
- alias visibility from `ChunkAliasTracker`;
- mutation/ticking policy from `ClientActionDiagnostics` and chunk tick checks;
- generation-only deferred writes from `WorldGenSpillover`.

The biggest challenge is keeping server authority and presentation helpers
separate. `ChunkAliasTracker` is presentation bookkeeping, while
`ServerChunkCacheMixin` and `LevelSetBlockBroadcastMixin` are authoritative
storage hooks. A single `TopologyContext` can expose both, but the API should
make authority explicit so packet fanout decisions do not leak into saved-state
ownership decisions.

## Packet Virtualization

Viability: high, with a strong case for a registry/table.

The v1 packet shape already matches the v2 plan: keep the vanilla-shaped client
cache and relabel outbound packets per receiver.

Current anchors:

- Full chunk packets are built from the canonical chunk and relabeled to the raw
  alias chunk in `PlayerChunkSenderMixin` (`PlayerChunkSenderMixin.java:94`).
  The same hook records loaded aliases in `ChunkAliasTracker`
  (`PlayerChunkSenderMixin.java:116`). Forget packets stay at the raw alias and
  remove that visibility record (`PlayerChunkSenderMixin.java:129`).
- Block, block-entity, section, and light packets are virtualized in
  `BlockPacketUtil` (`BlockPacketUtil.java:25`) and can fan out to every loaded
  alias (`BlockPacketUtil.java:42`, `BlockPacketUtil.java:115`,
  `BlockPacketUtil.java:138`, `BlockPacketUtil.java:164`,
  `BlockPacketUtil.java:188`).
- Biome resend packets also use loaded alias fanout with nearest-alias fallback
  (`ChunkPacketUtil.java:18`, `ChunkPacketUtil.java:40`).
- World-event packets virtualize sounds, block events, block break progress,
  particles, and explosions in one utility (`WorldEventPacketUtil.java:27`,
  `WorldEventPacketUtil.java:37`). Broadcast range checks use wrapped distance
  before sending (`PlayerListBroadcastMixin.java:28`).
- Entity packets virtualize add, absolute sync, teleport, damage source,
  vehicle correction, minecart interpolation, and bundles in `EntityPacketUtil`
  (`EntityPacketUtil.java:23`, `EntityPacketUtil.java:64`,
  `EntityPacketUtil.java:84`, `EntityPacketUtil.java:93`,
  `EntityPacketUtil.java:104`, `EntityPacketUtil.java:123`,
  `EntityPacketUtil.java:131`).
- Entity tracking stores per-viewer virtual tile offsets and sends absolute sync
  packets when an entity crosses the viewer-facing tile threshold
  (`ChunkMapTrackedEntityMixin.java:43`, `ChunkMapTrackedEntityMixin.java:87`,
  `ChunkMapTrackedEntityMixin.java:111`).
- Look-at and sign-editor packets are handled by a targeted player packet mixin
  (`ServerPlayerInteractionPacketMixin.java:23`,
  `ServerPlayerInteractionPacketMixin.java:45`).

The v2 opportunity is to replace the implicit "packet utility plus hook"
inventory with an explicit policy table. The table should distinguish:

- nearest-alias virtualization, such as ordinary sound/particle packets;
- loaded-alias fanout, such as block updates and block events;
- canonical-data/alias-header packets, such as full chunks;
- no-op relative packets, such as relative entity movement and velocity.

The challenge is that packet semantics matter more than field types. A codec or
reflection-driven transformer would be tempting but unsafe: `EntityPacketUtil`
must preserve relative X/Z flags (`EntityPacketUtil.java:93`), minecart steps
(`EntityPacketUtil.java:131`), damage source meaning (`EntityPacketUtil.java:104`),
and bundle shape (`EntityPacketUtil.java:50`). Handwritten policies remain the
safer default.

## Entities And AI

Viability: high for shared target views, moderate for replacing vanilla entity
queries.

The v2 actor-local target concept already exists in `AiAliasUtil`:

- `nearestAliasPosition`, `nearestAliasEyePosition`, and
  `nearestAliasBlockPos` calculate actor-local target coordinates
  (`AiAliasUtil.java:31`, `AiAliasUtil.java:63`, `AiAliasUtil.java:99`).
- `pathTargetBlockPositions` gives pathing several candidate aliases for small
  tiles while leaving vanilla pathfinding intact (`AiAliasUtil.java:128`).
- `distanceToSqr`, `horizontalDistanceToSqr`, `nearestAliasBoundingBox`, and
  `aliasLineOfSight` centralize common AI decisions (`AiAliasUtil.java:141`,
  `AiAliasUtil.java:163`, `AiAliasUtil.java:186`, `AiAliasUtil.java:200`).
- `ServerEntityGetterMixin` already teaches vanilla nearest-entity selection to
  use alias distance when there is a source actor (`ServerEntityGetterMixin.java:19`,
  `ServerEntityGetterMixin.java:42`).
- Player reach and attack range use visible alias boxes in
  `PlayerInteractionRangeMixin` (`PlayerInteractionRangeMixin.java:17`,
  `PlayerInteractionRangeMixin.java:29`, `PlayerInteractionRangeMixin.java:41`).

This strongly supports a v2 `ActorLocalTargetView` or similarly named record.
It could package the real entity, canonical position, actor-local position,
actor-local hitbox, wrapped distance, and alias line-of-sight result. That would
let the many current AI/ranged attack mixins consume one shared object instead
of asking for separate helper calls.

The challenge is coverage. V1 has many targeted mixins because vanilla AI does
not use one target abstraction consistently. Some hooks patch distances, some
patch look vectors, some patch path block targets, and ranged attacks often have
class-specific projectile math. V2 can reduce duplicated math, but it should
expect adapters to remain for custom vanilla control flow.

Replacing raw `EntityGetter` behavior globally is also risky. `AiAliasUtil`
canonicalizes query boxes (`AiAliasUtil.java:172`) and can add actor-local boxes
(`AiAliasUtil.java:179`), but some vanilla callers expect raw spatial queries.
V2 should prefer explicit actor-local query APIs over changing every entity
query in the level.

## Topological Raycasts

Viability: moderate. This is one of the places where v2 would add a real new
primitive rather than mostly reorganizing existing code.

Current coverage is useful but fragmented:

- AI line of sight maps the target eye to an actor-local alias and then calls
  vanilla `clip` (`AiAliasUtil.java:200`). This proves alias endpoint selection,
  not a general wrapped block ray.
- Arrow entity collision keeps vanilla hits and adds wrapped entity hitboxes
  through `ProjectileAliasUtil.addWrappedEntityHits`
  (`AbstractArrowAliasCollisionMixin.java:21`,
  `ProjectileAliasUtil.java:26`). The result still references the real entity,
  which is exactly the canonical identity model v2 wants.
- `ProjectileAliasUtil` can also extend splash-potion style area queries with
  canonical and alias-frame entities (`ProjectileAliasUtil.java:59`).
- Client picking has a separate curved ray implementation in
  `GlobeCurvedRaycast`, including block and entity picking
  (`GlobeCurvedRaycast.java:25`, `GlobeCurvedRaycast.java:60`,
  `GlobeCurvedRaycast.java:105`). `LocalPlayerMixin` replaces local picking
  with this helper (`LocalPlayerMixin.java:14`).

The missing primitive is a server-authoritative topological block clip/swept
movement API. Projectile launch vectors, arrow entity hits, and fishing
owner/pullback behavior can be alias-aware, but general projectile block
collision still follows vanilla space unless a specific projectile path has
been patched.

V2 should define bounded scan rules early. For short interactions, testing the
nearest alias frame is enough. For long rays or very small tiles, the ray can
cross multiple periods; scanning unbounded aliases would be expensive and could
produce ambiguous "earliest" hits. A practical API should require max tile
crossings, clip mode, fluid mode, and expected authority frame.

## Worldgen Window

Viability: moderate to high, with high vanilla-version fragility.

V1 already behaves like an implicit generation window:

- Worldgen chunk reads map requested chunks into the physical `WorldGenRegion`
  cache when the wrapped alias is present (`WorldGenRegionMixin.java:49`,
  `WorldGenRegionMixin.java:72`).
- Block/fluid/block-entity reads canonicalize positions
  (`WorldGenRegionMixin.java:44`, `WorldGenRegionMixin.java:81`,
  `WorldGenRegionMixin.java:86`).
- `ensureCanWrite` allows wrapped writes when the canonical target is within the
  current generation step's write radius (`WorldGenRegionMixin.java:99`).
- `setBlock` writes canonical positions, enqueues deferred spillover when the
  physical cache cannot observe the target, and records expected state when it
  can (`WorldGenRegionMixin.java:141`).
- `WorldGenSpillover` stores deferred writes by dimension and canonical chunk,
  applies them only to canonical chunks, and skips writes when the observed
  state has diverged (`WorldGenSpillover.java:25`,
  `WorldGenSpillover.java:72`, `WorldGenSpillover.java:84`,
  `WorldGenSpillover.java:108`).
- `ChunkGeneratorMixin` scopes generation with `DimensionTiling.runWith`, skips
  non-canonical decoration, and applies spillover around decoration
  (`ChunkGeneratorMixin.java:36`).
- Structure references and shifted placement are handled by
  `ChunkGeneratorMixin` and `StructureStartMixin`
  (`ChunkGeneratorMixin.java:58`, `ChunkGeneratorMixin.java:81`,
  `ChunkGeneratorMixin.java:126`, `StructureStartMixin.java:19`).

This supports a v2 `GenerationWindow`, but the wrapper must preserve phase
semantics. `WorldGenRegion.ensureCanWrite`, `generatingStep.blockStateWriteRadius`,
structure reference creation, and structure placement shifts are not generic
runtime block mutation. A v2 window should live in worldgen code and own:

- canonical read/write mapping;
- physical cache availability checks;
- deferred spillover ownership;
- expected-state guards;
- structure-source shifts and canonical-only persistence.

The challenge is external generation providers. V1 hooks vanilla classes
directly; providers that bypass `WorldGenRegion` or mutate chunks in unusual
phases may still need a degraded path or compatibility adapters.

## Configuration And Diagnostics

Viability: high, but still worth doing.

The original settings record mixed several responsibilities:

- topology and generation: `mode`, `tileSize`, terrain modes, Nether tiling,
  Nether portal scale, forced progression structure policy;
- presentation: Overworld/Nether curvature percentages;
- runtime gameplay: day/night cycle mode and day length multiplier;
- saved defaults and migration/sanitization behavior in the same record.

Runtime commands already respect part of the desired separation. `/globeworld
config set` exposes curvature, day/night mode, and day length, but not tile
size, tiling mode, terrain mode, Nether tiling, portal scale, or forced
progression flags (`GlobeDebugCommands.java:99`). The client pause-menu path can
mutate saved settings and broadcast them in single-player
(`GlobeClientSettings.java:24`). Multiplayer sync happens at configuration
join time and during play (`GlobeWorldNetworking.java:29`,
`GlobeWorldNetworking.java:40`).

Diagnostics are the messier part. Some logs are debug-gated, such as chunk alias
cleanup and packet fanout (`ChunkAliasTracker.java:161`,
`BlockPacketUtil.java:283`, `ChunkPacketUtil.java:79`), but several
investigation logs still warn during normal execution, including client chunk
cache diagnostics, portal diagnostics, client block-action rejection, chunk
load diagnostics, spillover cleanup/staleness, and interesting block mutations
(`ClientActionDiagnostics.java:62`, `WorldGenSpillover.java:180`,
`WorldGenSpillover.java:197`, `LevelSetBlockBroadcastMixin.java:157`).

This has since been implemented by deleting the flat `TilingSettings` adapter
and splitting saved settings into smaller records. Because v2 does not need to
load v1 worlds, the new records have their own clean codecs instead of
preserving the old flat saved shape:

- `TopologySettings`
- `PresentationSettings`
- `GameplaySettings`

Diagnostics should become named channels so high-volume playtesting logs can be
enabled intentionally by command or config instead of living permanently at
warn level.

## Migration Notes

The safest v2 migration path is still layered, but the feature-complete v1 code
suggests a sharper order:

1. Add `TopologyContext` as a facade over `DimensionTiling` and `CoordUtil`.
   Keep return types as vanilla positions at first.
2. Move alias visibility and packet position decisions behind named services,
   using `ChunkAliasTracker`, `BlockPacketUtil`, `ChunkPacketUtil`,
   `WorldEventPacketUtil`, and `EntityPacketUtil` as the source behavior.
3. Introduce `ActorLocalTargetView` and migrate AI mixins gradually. Keep
   class-specific ranged attack adapters where vanilla side effects are
   intertwined.
4. Prototype topological block clipping and projectile swept collision behind
   debug-only or narrow projectile paths before replacing broader movement
   behavior.
5. Extract `GenerationWindow` only after preserving the existing spillover and
   structure-shift tests/manual cases.
6. Split saved settings into clean v2 schemas and keep diagnostics session-only.
   Reject v1 saved data clearly.

Do not start v2 by deleting v1 utilities. Most of them are the proven behavior
that v2 should name and contain.
