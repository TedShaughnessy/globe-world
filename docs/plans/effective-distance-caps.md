# Effective Render And Simulation Distance Caps

Status: implemented. Durable behavior now lives in:

- [Client mechanics](../mod-mechanics/client.md) for effective render-distance caps.
- [Chunk mechanics](../mod-mechanics/chunks.md) for server simulation-distance caps.
- [Vanilla chunk loading](../vanilla-mechanics/chunk-loading.md) for the vanilla source anchors.

## Implemented Policy

Globe World keeps the player/server settings unchanged and caps only effective
values in tiled dimensions.

Server simulation distance is capped per dimension at:

```text
min(configuredSimulationDistance, max(2, ceil(tileSizeChunks / 2)))
```

The cap is applied in `ServerChunkCache` for both construction and runtime
`setSimulationDistance(...)`, before the value reaches `DistanceManager`.
Vanilla simulation distance spreads across diagonal chunk neighbors with the
same cost as cardinal neighbors, so `ceil(tileSizeChunks / 2)` covers every
wrapped chunk in the canonical tile, including corners, whenever the configured
simulation distance is at least that large.

Client render distance keeps vanilla's existing effective value, including the
server-advertised view-distance cap, then applies Globe caps:

```text
effectiveRenderDistance = configuredEffectiveRenderDistance
effectiveRenderDistance = min(effectiveRenderDistance, curvatureHorizonCap)

if tileSizeChunks < 32 and curvaturePercent >= 50:
    effectiveRenderDistance = min(effectiveRenderDistance, max(2, tileSizeChunks))
```

Untiled dimensions behave like vanilla.

## Deliberate Non-Goal

Globe World does not cap client-side simulation distance in this version. Login
and runtime `ClientboundSetSimulationDistancePacket` values still carry the
configured global server value. Keeping those packets vanilla avoids
per-dimension client simulation-distance state and keeps the feature simple; the
server-side simulation pressure is the main target of this pass.

## Diagnostics

- `/globeworld debug` and `/globeworld debug pos` report configured and
  effective server simulation distance for the player's current dimension.
- The Globe client debug overlay reports configured and effective client render
  distance.

## Deferred Questions

- Should server view distance be capped in a later phase?
- Does simulation distance need a one-chunk seam padding beyond whole-tile
  coverage for redstone, fluid, or entity edge cases?
- Does the client-side simulation-distance mismatch ever become visible enough
  to justify per-dimension simulation-distance packets?
