# Topology

## What

Globe World treats X/Z as periodic. A raw position can be anywhere in vanilla
coordinate space, but mutable state is stored at its canonical equivalent inside
the configured tile.

Tiling is also dimension-specific:

- The Overworld uses `mode` and `tile_size`.
- The Overworld terrain method uses `terrain_mode`, with `auto` deriving the
  method from `tile_size`.
- The Nether uses `nether_mode` and `nether_tile_size`.
- The Nether terrain method uses `nether_terrain_mode`, with `auto` deriving
  the method from `nether_tile_size`.
- Nether portals use `nether_portal_scale_numerator` and
  `nether_portal_scale_denominator` for Overworld/Nether coordinate scaling.
- The End never tiles.

## Why

Every gameplay system needs the same answer to coordinate identity questions. If
chunk lookup, entity tracking, block packets, and worldgen each invent their own
wrapping math, edge behavior will drift and aliases will either desync or
duplicate state.

Vanilla dimensions also do not share one coordinate scale. Nether portals
normally have an 8:1 relation with the Overworld, while Globe World can save a
different Overworld/Nether portal ratio and the End should remain vanilla.
Keeping tiling policy dimension-aware avoids applying Overworld topology to
dimensions where it does not fit.

## Coordinate Helpers

The coordinate helper layer answers four questions:

- What canonical chunk/block corresponds to this raw coordinate?
- Which tile alias does this raw coordinate belong to?
- What is the shortest wrapped X/Z distance between two positions?
- Where should a canonical object be rendered relative to a specific viewer?

`TileGeometry` is the two-dimensional topology boundary. Square worlds use
`SquareTileGeometry`, which preserves the original independent X/Z period.
Experimental hex worlds use `HexTileGeometry`, which defines a chunk-composed
canonical mask plus two lattice translation vectors; the third edge-pair
translation is derived from those vectors.

The geometry also exposes diagnostic metadata without requiring callers to cast
to its square or hex implementation:

- a save-facing geometry revision (`square-v1` or `hex-top-bottom-v1`);
- lattice basis vectors `A` and `B`;
- the integer lattice coordinate `(k, l)` for a raw chunk;
- the corresponding `k*A + l*B` translation;
- exact exposed chunk-edge boundary segments and the neighboring alias reached
  across each segment.

Boundary segments label the six hex seam directions as `±A`, `±B`, and
`±(A-B)`. The command and client debug paths consume this shared description,
so displayed boundaries and alias coordinates use the same ownership decisions
as runtime canonicalization.

`TopologyContext` is the named runtime boundary for this math. It wraps a
dimension and its effective `DimensionTiling`, then delegates whole-position
questions to `TileGeometry` through frame-named helpers such as
`canonicalBlock`, `canonicalChunk`, `canonicalBox`, `virtualBlockForViewer`,
`virtualChunkForViewer`, `virtualBoxForViewer`, `wrappedDistanceSqr`,
`loadedAliasesFor`, and `shouldAllowAliasMutation`. Runtime block/chunk access
helpers use these names at subsystem boundaries. `CoordUtil` still keeps
square-friendly axis helpers for older call sites, but hex-correct code must use
whole-position geometry methods because one axis alone is not enough to choose a
hex owner.

Tile geometries are immutable and cached by effective `DimensionTiling`;
dimension contexts are likewise cached by dimension and tiling settings. This
is required for hex worldgen performance because building `HexTileGeometry`
derives the canonical mask bounds, while block/chunk canonicalization is a hot
path that may run millions of times during initial generation.

Canonicalization is used before state access. Virtualization is used when
building viewer-facing positions, especially packets and tracking decisions.

## Experimental Hex Topology

`TilingMode.HEX` can be saved for the Overworld. At runtime it is enabled
topology, normalizes `tile_size` to a multiple of four chunks with a minimum of
eight chunks, and resolves terrain mode to `EDGE_BLEND`. Nether hex topology is
not enabled yet; Nether wrapping still only activates for square mode.

The first hex mask is fixed-orientation and chunk-precision. With X drawn
horizontally and Z vertically, the implemented mask has its narrow tips at the
top and bottom; this differs from the left/right-pointed orientation proposed in
the original plan. It uses integer lattice translation vectors `A`, `B`, and
`A - B`; every raw chunk chooses the nearest lattice copy and maps back to one
canonical owner. Block canonicalization canonicalizes the containing chunk
first and applies the same whole-chunk translation to the block-local
coordinate. Nearest visible aliases are chosen with a bounded candidate search
around the viewer rather than closed-form math.

For the minimum saved width of `8` chunks, the normalized mask has `64`
canonical chunks. Its lattice translations are `(8, 0)`, `(4, 8)`, and
`(4, -8)` chunks, or `(128, 0)`, `(64, 128)`, and `(64, -128)` blocks. The
canonical rows run from Z chunk `-5` through `4`: the two tip rows contain two
chunks, the next rows contain six, and the six middle rows contain eight.
Translating the complete mask by any of the six signed lattice vectors covers
the neighboring copy without changing canonical ownership.

The first run is a runtime topology slice, not a seamless generation milestone.
Chunk ownership, block mutation, packet relabeling, entity tracking/query
helpers, POI broad queries, game events, explosions, and bounded worldgen
ownership helpers use the geometry boundary. Terrain, biome, cave, feature,
structure, local-solar-time, and Atlas projection continuity remain square-first
or explicitly deferred for later hex work.

## Topological Entity Queries

`TopologicalEntityQueries` is the shared boundary for broad entity lookup
boxes. It keeps vanilla entity identity and predicates, but gathers candidates
from every canonical slice touched by a visible-frame query box. A query near
the canonical tile edge is split across the wrapped X/Z edges instead of only
wrapping the box center.

The helper dedupes by entity identity, includes canonical non-player storage,
and adds server players whose nearest visible alias intersects the query box.
`ActorLocalTargets` exposes actor-facing adapters such as
`targetsInActorRange(...)`, while projectile, pickup, container-open, sensor,
and `ServerEntityGetter` hooks use the same lower-level primitive.

## Topological Raycast Primitives

`TopologicalRaycasts` is the shared boundary for ray-like topology queries. It
keeps vanilla clip modes explicit while returning both visible-frame hit data
and canonical hit identity:

- `topologicalClip(...)` runs a block/fluid clip in the caller's visible frame
  and canonicalizes the hit block position and hit location.
- `topologicalEntitySweep(...)` tests canonical entity identity through
  visible alias hitboxes, returning the earliest visible hit per entity.
- `topologicalLineOfSight(...)` maps a target into an actor-local frame before
  doing a collider clip.
- `topologicalViewVector(...)` mirrors vanilla's shared view-vector ray helper
  for server-side item validation paths such as brush targeting.
- `topologicalHitEntitiesAlong(...)` mirrors vanilla's shared attack-range ray
  helper for server-side component weapons.
- `topologicalProjectileMove(...)` packages block clipping and entity sweep for
  server-authoritative projectile movement.

The default entity alias radius is intentionally zero, matching the existing v1
projectile behavior of testing the nearest alias frame to the ray origin. Wider
alias scans are opt-in through `EntitySweepOptions.withAliasTileRadius(...)` so
tiny-tile experiments have an explicit cost cap.

Block trace results also expose `visibleHitWithCanonicalBlock()`, which keeps
the hit location in the caller's visible frame but replaces the block position
with the canonical owner. Arrow block clipping uses this adapter so vanilla
movement and entity ordering can stay visible-frame while block callbacks and
state lookups receive canonical identity.

`ProjectileUtilTopologicalMoveMixin` applies these primitives to vanilla's
shared server-side `ProjectileUtil` ray helpers: move-vector projectile hits,
view-vector hits, and attack-range entity sweeps. Client-side projectile
prediction stays on vanilla's raw helper for now, while server-authoritative hit
results supply canonical block/entity identity.

## Dimension Policy

`DimensionTiling` resolves the effective tiling context for Overworld, Nether,
and End. Runtime paths use level/player dimension context where available.
Worldgen paths that do not receive a `ServerLevel` use a scoped
`DimensionTiling` context. Scoped context helpers restore any previous tiling
after nested worldgen calls, cancellations, or exceptions, so Nether generation
does not accidentally inherit the Overworld fallback.

`nether_tile_size` is saved directly and is independent from the Overworld tile
size. This permits a smaller Nether tile, same-size Nether tile, larger Nether
tile, or no Nether tiling when `nether_mode` is disabled.

Saved tile sizes are normalized to even chunk counts. This keeps the canonical
chunk interval and canonical block interval aligned at tile edges; odd custom
inputs round up to the next even size.

Nether portal scale is also independent from tile size. The saved numerator and
denominator describe Nether-to-Overworld coordinate scaling:

```text
overworldCoordinate = netherCoordinate * numerator / denominator
```

Allowed portal ratios are `1/32`, `1/16`, `1/8`, `1/4`, `1/2`, `1/1`, `2/1`,
`4/1`, `8/1`, `16/1`, and `32/1`. The default is `8/1`, matching vanilla-style
fast Overworld travel through the Nether. Reverse ratios such as `1/8` make
eight Nether blocks map to one Overworld block.

## Key Files

- `mod-fabric/src/main/java/globe/world/util/CoordUtil.java`
- `mod-fabric/src/main/java/globe/world/util/DimensionTiling.java`
- `mod-fabric/src/main/java/globe/world/topology/TileGeometry.java`
- `mod-fabric/src/main/java/globe/world/topology/SquareTileGeometry.java`
- `mod-fabric/src/main/java/globe/world/topology/HexTileGeometry.java`
- `mod-fabric/src/main/java/globe/world/topology/TopologyContext.java`
- `mod-fabric/src/main/java/globe/world/topology/TopologyContexts.java`
- `mod-fabric/src/main/java/globe/world/topology/TopologicalEntityQueries.java`
- `mod-fabric/src/main/java/globe/world/topology/TopologicalPoiQueries.java`
- `mod-fabric/src/main/java/globe/world/topology/TopologicalRaycasts.java`
- `mod-fabric/src/main/java/globe/world/topology/TopologicalExplosions.java`
- `mod-fabric/src/main/java/globe/world/config/TopologySettings.java`
- `mod-fabric/src/main/java/globe/world/config/GlobeSettings.java`
- `mod-fabric/src/main/java/globe/world/config/GlobeConfig.java`
- `mod-fabric/src/main/java/globe/world/mixin/WorldGenSettingsMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/ServerLevelTicksDimensionMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/NetherPortalBlockMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/PortalForcerMixin.java`
- `mod-fabric/src/main/java/globe/world/mixin/PortalProcessorMixin.java`

## Implemented Paths

- Runtime chunk, block, entity packet, and waypoint packet paths use dimension
  context.
- Experimental Overworld hex topology is implemented for runtime chunk/block
  ownership, nearest aliases, wrapped distances, broad query boxes, chunk
  packet relabeling, block packet fanout, entity packet virtualization, entity
  tracking, and geometry tests.
- Server chunk lookup, alias tickets, random ticks, spawning collection,
  tracking, block mutation, and worldgen region access use dimension-aware
  wrapping.
- Worldgen and natural-spawn paths that still need ambient coordinate helpers
  enter them through scoped `DimensionTiling` wrappers.
- Players are rebased to canonical X/Z on login, bed wake-up, and respawn.
- Scheduled tick containers are tagged with their `ServerLevel` dimension when
  exposed by `ServerLevel`.
- Nether portal approximate exits canonicalize the source X/Z before applying
  Globe World's configured Overworld/Nether portal scale, then canonicalize the
  target dimension before portal search/creation. Existing-portal POI loading
  and lookup split the visible search square through the geometry, dedupe
  canonical portal records, and rank them by wrapped distance. Other dimension
  pairs keep vanilla's dimension scale. This keeps different aliases of the
  same source portal from creating separate scaled target portals and lets all
  six hex seams find the same nearby canonical portal.
- `/globeworld pos` reports the current dimension's effective tiling, current
  alias, canonical position, longitude offset, and local solar day tick.
- `/globeworld border_distance` reports distance from the executing player's
  canonical position to each tile border.
- `/globeworld portal_scale` reports the saved Nether portal ratio and the
  exact Nether-to-Overworld and Overworld-to-Nether multipliers.
- `/globeworld teleport_canon` teleports the executing player to the canonical
  X/Z equivalent of their current visual alias.
- `/globeworld teleport_border [inset]` teleports the executing player near the
  nearest canonical tile border for seam testing.
- `/globeworld teleport_alias <tileX> <tileZ>` teleports the executing player to
  a chosen whole-tile visual alias of their current canonical position.
- `/globeworld config` reports saved tiling settings.

Tile size, Overworld tiling mode, Overworld terrain method, Nether tiling mode,
Nether tile size, Nether terrain method, and Nether portal scale are treated as
permanent world-topology settings. They are visible through `/globeworld
config`, but intentionally are not mutable through runtime commands.

`GlobeSettings` is the saved server model under the `globe_world` field in
vanilla `WorldGenSettings`. It serializes durable topology, presentation, and
gameplay groups. Diagnostics remain session-local command state and are not part
of saved settings. The codec accepts the current split schema; pre-release
scratch schemas are not imported. `GlobeSettingsHolder` is the world-creation
and saved-settings boundary. `TopologySettings`, `PresentationSettings`, and
`GameplaySettings` own their normalization and update helpers directly.
`DimensionTiling` reads topology through `TopologySettings`, and the settings UI
edits topology, presentation, and gameplay as separate `GlobeSettings` sections.
Runtime commands still mutate only presentation/gameplay fields such as
curvature, day/night mode, and day-length multiplier.

## Related Vanilla Mechanics

- [Vanilla chunk loading](../vanilla-mechanics/chunk-loading.md)
- [Vanilla block updates](../vanilla-mechanics/block-updates.md)
- [Vanilla mobs and entities](../vanilla-mechanics/mobs-and-entities.md)
- [Vanilla world generation](../vanilla-mechanics/world-generation.md)

## Open Audits

- Nether terrain/noise periodicity across all generation phases.
- Nether portal round trips at aliases and canonical positions.
- End portal and End dimension behavior staying vanilla.
- Natural spawning near Nether tile edges.
- Structures/features near Nether tile edges.
