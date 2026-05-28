# Client

## What

The client sees raw alias chunks as normal chunks. It does not own canonical
world authority; the server sends ordinary-looking packets at the alias
coordinates the player is tracking.

Client visuals can add presentation-only effects, such as terrain curvature,
without changing canonical server state.

## Why

Minecraft's client chunk cache, renderer, and networking are position-keyed. The
least invasive way to render multiple aliases is to keep packets in vanilla
shape and change their coordinates before the client receives them.

## Implementation

Full chunk packets are sourced from canonical chunk data and relabeled to the
alias chunk position. Block, section, block-entity, and entity packets are copied
or virtualized per viewer.

The curvature visual pass rewrites relevant world vertex shaders at resource
load time. Curvature is saved in `TilingSettings`, exposed through the world
creation and pause/options UI, and can be disabled with `0%`.

Local solar-time day/night is planned but not implemented. The repository
already has a saved `DayNightCycleMode` enum with `REALISTIC`, and the
world-creation UI can select it. The remaining client work is rendering:
camera-position-aware environment attribute sampling so the sky and lightmap use
local canonical X. The feature also needs server gameplay hooks for local
sleeping, spawning, mob burning, and other day/night predicates.

Client diagnostics and debug overlays remain targeted at alias loading,
tracking, tile borders, and settings state.

## Key Files

- `src/main/java/globe/world/mixin/ClientboundLevelChunkWithLightMixin.java`
- `src/main/java/globe/world/util/BlockPacketUtil.java`
- `src/main/java/globe/world/util/EntityPacketUtil.java`
- `src/client/java/globe/world/client/GlobeCurvatureShader.java`
- `src/client/java/globe/world/client/GlobeCurvatureSlider.java`
- `src/client/java/globe/world/client/GlobeClientTilingSettings.java`
- `src/main/java/globe/world/config/DayNightCycleMode.java`
- `src/client/java/globe/world/client/GlobeTileBorderRenderer.java`
- `src/client/java/globe/world/client/GlobeDebugHud.java`
- `src/client/java/globe/world/client/mixin/ShaderManagerMixin.java`
- `src/client/java/globe/world/client/mixin/CreateWorldScreenMixin.java`
- `src/client/java/globe/world/client/mixin/OptionsScreenMixin.java`
- `src/client/java/globe/world/client/mixin/SectionOcclusionGraphMixin.java`
- `src/client/java/globe/world/client/mixin/FrustumMixin.java`

## Related Plans

- [Client Canonical Chunk Cache](../plans/client-canonical-chunk-cache.md)
- [Realistic Globe Lighting](../plans/realistic-lighting-plan.md)

## Related Vanilla Mechanics

- [Vanilla client world](../vanilla-mechanics/client-world.md)
- [Vanilla lighting](../vanilla-mechanics/lighting.md)
- [Vanilla time and sky](../vanilla-mechanics/time-and-sky.md)

## Open Audits

- Client-side canonical chunk cache is still planned, not authoritative.
- Realistic local solar time is still planned, not implemented.
- Realistic day/night gameplay needs separate server-side audits for sleeping,
  spawning, mob burning, and other global time predicates.
