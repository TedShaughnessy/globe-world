# Hexagonal Tiles First Run

## Goal

Implement an experimental first pass of hexagonal tiles where runtime topology
works before terrain continuity does. The target is a playable diagnostic slice:
chunks and blocks have one canonical owner, aliases render and route mutations
to that owner, and players can manually test all six edges.

Seamless generation, true edge blending, structure continuity, and Atlas polish
are explicitly out of scope for this first run.

## Scope

The first run should:

- add `HEX` as an experimental tiling mode;
- introduce a two-dimensional tile geometry boundary;
- preserve existing square tiling behavior through the same boundary;
- define one fixed left/right-pointed discrete hex chunk mask;
- canonicalize chunks and blocks through that mask;
- choose nearest visible aliases by hex lattice translation;
- update chunk loading, packet relabeling, block mutation, entity lookup, and
  ray/query helpers enough for manual playtesting;
- keep worldgen simple and accept visible terrain discontinuities at hex edges.

The first run should not:

- implement seamless six-edge terrain blending;
- implement true oblique periodic noise;
- solve structure placement across hex seams;
- redesign the Atlas projection;
- replace or change existing square-world save behavior.

## Implementation Plan

### 1. Add A Tile Geometry Boundary

Create a small `TileGeometry` API under `globe.world.topology`. It should answer
two-dimensional topology questions instead of exposing independent X/Z wrapping.

Initial methods should include:

- `canonicalChunk(int rawX, int rawZ)`;
- `canonicalBlock(int rawX, int y, int rawZ)`;
- `canonicalBlock(Vec3 raw)`;
- `isCanonicalChunk(ChunkPos pos)`;
- `isCanonicalBlock(BlockPos pos)`;
- `nearestAlias(ChunkPos canonical, ChunkPos viewer)`;
- `nearestAlias(BlockPos canonical, Vec3 viewer)`;
- `nearestAlias(Vec3 canonical, Vec3 viewer)`;
- `canonicalBox(AABB visibleBox)`;
- `virtualBoxForViewer(AABB canonicalBox, Vec3 viewer)`;
- `wrappedDistanceSqr(Vec3 a, Vec3 b)`;
- `wrappedChunkDistanceSqr(ChunkPos chunk, Vec3 pos)`;
- `canonicalQueryBoxes(AABB visibleBox)`.

Add `SquareTileGeometry` first and make `TopologyContext` delegate to it while
preserving current square behavior.

### 2. Carry Tiling Mode At Runtime

Extend `DimensionTiling` so it carries `TilingMode`, tile size, enabled state,
and terrain mode together. Then update `TopologySettings` and `TilingMode` so
`HEX` can be saved and displayed without changing square saves.

For hex mode:

- treat it as enabled topology;
- sanitize its tile size separately from square mode;
- force or resolve terrain mode to `EDGE_BLEND` for now;
- keep Nether hex disabled unless explicitly enabled later.

### 3. Define The Discrete Hex Mask

Implement one fixed left/right-pointed hex orientation. The canonical tile is a
chunk mask, not a block-precision polygon.

The mask must satisfy:

- whole chunks only;
- paired opposite sides have equal chunk-edge spans;
- left and right tips are two chunks wide;
- each raw chunk maps to exactly one canonical owner;
- canonical chunks map to themselves;
- every canonical owner is reachable from raw aliases by two independent
  lattice vectors.

Pick one saved size meaning for the first run. The simplest choice is to keep
using `tile_size` as an approximate hex width in chunks, then normalize it to
valid hex dimensions internally.

### 4. Implement Hex Canonicalization

Add `HexTileGeometry`.

The geometry should define:

- the canonical chunk mask;
- two lattice translation vectors `A` and `B`;
- the derived third edge-pair translation;
- a deterministic mapping from raw chunk to canonical owner;
- block canonicalization by canonicalizing the containing chunk first, then
  applying the same chunk translation to the block-local coordinate;
- nearest-alias lookup by searching nearby lattice offsets around the viewer.

For the first version, prefer a small bounded candidate search over clever math.
The goal is correctness, readable tests, and easy debugging.

### 5. Migrate TopologyContext To Geometry

Update `TopologyContext` so its public methods are mostly geometry delegates.
Keep compatibility helpers like `canonicalChunkX(...)` only where existing code
still needs them, and avoid adding new one-axis call sites.

Priority methods:

- `canonicalChunk(...)`;
- `canonicalBlock(...)`;
- `canonicalBox(...)`;
- `virtualChunkForViewer(...)`;
- `virtualBlockForViewer(...)`;
- `virtualBoxForViewer(...)`;
- `wrappedDistanceSqr(...)`;
- `wrappedChunkDistanceSqr(...)`;
- `aliasMutationAccess(...)`.

### 6. Make Chunk Ownership And Packet Aliases Work

Update runtime chunk paths so alias coordinates resolve through the geometry
instead of independent tile X/Z periods.

Initial target files:

- `ChunkAliasTracker`;
- `CanonicalChunkTickets`;
- `ChunkPacketUtil`;
- `BlockPacketUtil`;
- `PlayerChunkSenderMixin`;
- `ChunkMapPlayerProviderMixin`;
- `ChunkMapSpawningMixin`;
- `ServerChunkCacheMixin`.

For hex v1, alias enumeration may brute-force nearby lattice offsets within
view distance. Avoid optimizing this until manual behavior is proven.

### 7. Update Runtime Queries With Conservative Coverage

Entity, POI, raycast, collision, game-event, and explosion paths need to ask the
geometry for canonical/visible frames.

Initial target files:

- `TopologicalEntityQueries`;
- `TopologicalRaycasts`;
- `TopologicalPoiQueries`;
- `TopologicalCollisionQueries`;
- `TopologicalGameEvents`;
- `TopologicalExplosions`;
- `ActorLocalTargets`.

For broad queries, allow over-covering at first. Query a small set of shifted
boxes that cover all nearby aliases touched by the visible box, then rely on
existing identity dedupe and final distance checks.

### 8. Keep Worldgen Deliberately Limited

Do only the minimum worldgen work required to avoid obvious ownership bugs:

- route existing canonical chunk/block reads and writes through the geometry;
- ensure `GenerationWindow` classifies hex canonical destinations correctly
  when it is used;
- avoid implementing six-edge terrain blending in this milestone;
- document that terrain, biome, feature, cave, and structure seams may be
  visibly discontinuous.

The first hex milestone is a topology milestone, not a generation milestone.

### 9. Add Tests Before Manual Playtesting

Add pure tests for the geometry before broad gameplay testing. The useful test
set is small and mechanical:

- square geometry matches current wrapping;
- every sampled raw chunk maps into the canonical hex mask;
- canonical chunks map to themselves;
- neighboring raw aliases map to the same canonical owner across all six edges;
- block canonicalization preserves block-local coordinates after chunk
  translation;
- nearest alias picks the expected visible copy near each edge;
- wrapped distance is symmetric;
- query boxes cover edge and corner cases without missing aliases.

If the current Gradle layout makes JVM tests awkward, add a small test-friendly
geometry package first rather than wiring tests through Minecraft runtime types.

## Manual Acceptance

The first run is successful when a manually configured hex world can:

- start without crashing;
- report sensible `/globeworld pos` data;
- teleport near each of the six edges;
- walk across each edge and stay in a coherent visible frame;
- load and unload chunks across edges;
- place and break blocks through aliases;
- receive block updates through aliases;
- interact with at least simple entities across the nearest edge;
- tolerate visible terrain discontinuity without corrupting canonical state.

## Suggested Order Of Work

1. Add `TileGeometry` and `SquareTileGeometry`.
2. Route `TopologyContext` through square geometry with no intended behavior
   change.
3. Add `HEX` config plumbing and runtime `TilingMode` on `DimensionTiling`.
4. Implement `HexTileGeometry` and pure tests.
5. Switch canonical chunk/block runtime paths to geometry.
6. Update chunk alias loading and packet relabeling.
7. Update block mutation/update paths.
8. Update entity/query/raycast paths.
9. Add hex diagnostics and manual edge-test commands.
10. Revisit seamless generation as the next milestone.

## Deferred Follow-Ups

- Six-edge and corner edge-blend terrain.
- Hex-aware Atlas projection.
- Structure starts, references, and piece placement across hex seams.
- Nether hex mode.
- Local solar time axis choice for hex worlds.
- Performance tuning for alias enumeration and broad query splitting.
